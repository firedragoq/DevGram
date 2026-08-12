package org.telegram.messenger.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

// DevGram: бэкенд «Camera X» для камеры кружков — CameraX Preview рендерит в наш GL SurfaceTexture.
// Интерфейс повторяет Camera2Session (open/destroy/setZoom/getMin|MaxZoom/isInitiated/...), чтобы
// встроиться в InstantCameraView как третий бэкенд. Всё в try/catch — при сбое InstantCameraView
// откатывается на Camera 2 (opt-in, включается только при явном выборе «Camera X»).
public class DevGramCameraXSession {

    private final boolean frontface;
    private final int width;
    private final int height;

    private ProcessCameraProvider provider;
    private Camera camera;
    private Preview preview;
    private volatile boolean initiated;
    private final SimpleLifecycleOwner lifecycleOwner = new SimpleLifecycleOwner();

    public DevGramCameraXSession(boolean frontface, int width, int height) {
        this.frontface = frontface;
        this.width = width;
        this.height = height;
    }

    // Вызывается на UI-потоке (createCamera → runOnUIThread). CameraX требует главного потока для bind.
    public void open(final SurfaceTexture surfaceTexture) {
        final Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || surfaceTexture == null) {
            return;
        }
        try {
            surfaceTexture.setDefaultBufferSize(width, height);
        } catch (Throwable ignore) {
        }
        try {
            final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(ctx);
            future.addListener(() -> {
                try {
                    provider = future.get();
                    lifecycleOwner.start();

                    preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(ContextCompat.getMainExecutor(ctx), request -> {
                        try {
                            Size res = request.getResolution();
                            surfaceTexture.setDefaultBufferSize(res.getWidth(), res.getHeight());
                            Surface surface = new Surface(surfaceTexture);
                            request.provideSurface(surface, ContextCompat.getMainExecutor(ctx), result -> {
                                try {
                                    surface.release();
                                } catch (Throwable ignore) {
                                }
                            });
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    });

                    CameraSelector selector = frontface
                            ? CameraSelector.DEFAULT_FRONT_CAMERA
                            : CameraSelector.DEFAULT_BACK_CAMERA;
                    provider.unbindAll();
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview);
                    initiated = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    initiated = false;
                }
            }, ContextCompat.getMainExecutor(ctx));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public boolean isInitiated() {
        return initiated;
    }

    public int getPreviewWidth() {
        return width;
    }

    public int getPreviewHeight() {
        return height;
    }

    public float getMaxZoom() {
        try {
            return camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        } catch (Throwable e) {
            return 1f;
        }
    }

    public float getMinZoom() {
        try {
            return camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
        } catch (Throwable e) {
            return 1f;
        }
    }

    public void setZoom(float ratio) {
        try {
            if (camera != null) {
                camera.getCameraControl().setZoomRatio(ratio);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void setFlash(boolean on) {
        try {
            if (camera != null) {
                camera.getCameraControl().enableTorch(on);
            }
        } catch (Throwable ignore) {
        }
    }

    public void setRecordingVideo(boolean recording) {
        // CameraX-Preview пишет в наш GL SurfaceTexture, отдельная логика записи не нужна.
    }

    public void destroy(boolean async) {
        destroy(async, null);
    }

    public void destroy(boolean async, Runnable afterCallback) {
        Runnable r = () -> {
            try {
                initiated = false;
                if (provider != null) {
                    provider.unbindAll();
                }
                lifecycleOwner.stop();
                camera = null;
                preview = null;
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (afterCallback != null) {
                afterCallback.run();
            }
        };
        try {
            ContextCompat.getMainExecutor(ApplicationLoader.applicationContext).execute(r);
        } catch (Throwable e) {
            r.run();
        }
    }

    // Простой LifecycleOwner для CameraX bindToLifecycle (InstantCameraView сам не LifecycleOwner).
    static class SimpleLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry registry = new LifecycleRegistry(this);

        SimpleLifecycleOwner() {
            registry.setCurrentState(Lifecycle.State.INITIALIZED);
        }

        void start() {
            try {
                registry.setCurrentState(Lifecycle.State.RESUMED);
            } catch (Throwable ignore) {
            }
        }

        void stop() {
            try {
                registry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (Throwable ignore) {
            }
        }

        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }
}
