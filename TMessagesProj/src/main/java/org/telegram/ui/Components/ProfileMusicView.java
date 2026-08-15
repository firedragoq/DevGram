package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileMusicView extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private Text author, title;
    private final Paint arrowPaint = new Paint();
    private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    private final RectF rect = new RectF();
    private final RectF playRect = new RectF();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private final ButtonBounce bounce = new ButtonBounce(this);
    private final ButtonBounce playBounce = new ButtonBounce(this);

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPath.moveTo(0, -dpf2(3.33f));
        arrowPath.lineTo(dpf2(3.16f), 0);
        arrowPath.lineTo(0, dpf2(3.33f));

        setColor(null);
        setText("Author", " - Title");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                dp(37),
                MeasureSpec.EXACTLY
            )
        );
    }

    private int textColor = Color.WHITE;
    private float parentExpanded;
    private int backgroundColor;
    private int accentColor;
    private int controlIconColor;
    private boolean withShadows;

    public void setColor(MessagesController.PeerColor peerColor) {
        int color1, color2;
        if (peerColor == null) {
            color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
        } else {
            color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        }

        if (peerColor == null) {
            backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            controlIconColor = Color.WHITE;
            withShadows = true;
        } else {
            backgroundColor = Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .15f), +.04f, -.09f);
            accentColor = ColorUtils.setAlphaComponent(Color.WHITE, 52);
            controlIconColor = Color.WHITE;
            withShadows = false;
        }
        backgroundPaint.setColor(backgroundColor);
        controlPaint.setColor(accentColor);
        controlIconPaint.setColor(controlIconColor);
        controlIconPaint.setStrokeWidth(dpf2(1.7f));
        controlIconPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(peerColor == null ? accentColor : Color.WHITE);
        checkTextColor();
    }

    private void checkTextColor() {
        final boolean useBlackText = parentExpanded < 0.8f && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.85f;
        textColor = useBlackText ? Color.BLACK : Color.WHITE;
        arrowPaint.setColor(Theme.multAlpha(textColor, 0.85f));
        invalidate();
    }

    public void setParentExpanded(float expanded) {
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            checkTextColor();
            invalidate();
        }
    }



    public void setMusicDocument(TLRPC.Document document) {
        CharSequence author = getAuthor(document);
        CharSequence title = getTitle(document);
        if (TextUtils.isEmpty(author)) {
            if (TextUtils.isEmpty(title)) {
                author = getString(R.string.AudioUnknownArtist);
                title = " - " + getString(R.string.AudioUnknownTitle);
            } else {
                author = "";
            }
        } else if (!TextUtils.isEmpty(title)) {
            title = " - " + title;
        } else {
            title = "";
        }
        setText(author, title);
    }

    public static CharSequence getTitle(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                String title = attribute.title;
                if (title == null || title.length() == 0) {
                    title = FileLoader.getDocumentFileName(document);
                }
                return title;
            }
        }
        String fileName = FileLoader.getDocumentFileName(document);
        if (!TextUtils.isEmpty(fileName)) {
            return fileName;
        }
//        return getString(R.string.AudioUnknownTitle);
        return null;
    }

    public static CharSequence getAuthor(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (!attribute.voice) {
                    return attribute.performer;
                }
            }
        }
        return null;
    }

    public void setText(CharSequence author, CharSequence title) {
        this.author = new Text(author, 11, AndroidUtilities.bold());
        this.title = new Text(title, 11);

        setContentDescription(getString(R.string.AccDescrProfileMusic) + " " + author + " — " + title);
    }

    private Runnable onPlayClickListener;
    private boolean currentTrack;
    private boolean playing;
    private float playbackProgress;

    public void setOnPlayClickListener(Runnable listener) {
        onPlayClickListener = listener;
    }

    public void setPlaybackState(boolean currentTrack, boolean playing, float progress) {
        progress = Utilities.clamp(progress, 1f, 0f);
        if (this.currentTrack != currentTrack || this.playing != playing || this.playbackProgress != progress) {
            this.currentTrack = currentTrack;
            this.playing = playing;
            this.playbackProgress = progress;
            invalidate();
        }
    }

    private ProfileActivity.AvatarImageView avatarView;
    private boolean ignoreRect = false;
    private RenderNode renderNode;
    private float renderNodeScale;
    private float renderNodeTranslateY;

    public void drawingBlur(boolean drawing) {
        if (ignoreRect != drawing || renderNode != null) {
            ignoreRect = drawing;
            renderNode = null;
            avatarView = null;
            invalidate();
        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
        this.ignoreRect = false;
        this.renderNode = renderNode;
        this.avatarView = avatarView;
        this.renderNodeScale = scale;
        this.renderNodeTranslateY = dy;
        invalidate();
    }

    private float currentHeight;
    private boolean touchCaptured;
    private boolean playTouch;

    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        setTranslationY(y - dp(12));
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final float alpha = Utilities.clamp01((currentHeight) / dp(21));
        if (alpha <= 0) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchCaptured = rect.contains(event.getX(), event.getY());
            playTouch = touchCaptured && playRect.contains(event.getX(), event.getY());
            playBounce.setPressed(playTouch);
            bounce.setPressed(touchCaptured && !playTouch);
            return touchCaptured;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && touchCaptured) {
            boolean inside = (playTouch ? playRect : rect).contains(event.getX(), event.getY());
            playBounce.setPressed(playTouch && inside);
            bounce.setPressed(!playTouch && inside);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
            playBounce.setPressed(false);
            touchCaptured = false;
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            boolean handled = touchCaptured;
            if (playBounce.isPressed() && onPlayClickListener != null) {
                onPlayClickListener.run();
            } else if (bounce.isPressed()) {
                performClick();
            }
            bounce.setPressed(false);
            playBounce.setPressed(false);
            touchCaptured = false;
            return handled;
        }
        return touchCaptured;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;

        final float alpha = Utilities.clamp01((currentHeight) / dp(21));
        final float scale = bounce.getScale(0.018f);
        if (alpha <= 0) return;

        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;
        final float chromeWidth = dp(currentTrack ? 66 : 52);
        final float maxTextWidth = Math.max(dp(36), maxWidth - chromeWidth);
        this.author.ellipsize(maxTextWidth * .48f);
        this.title.ellipsize(maxTextWidth - this.author.getWidth());

        final float textWidth = this.author.getWidth() + this.title.getWidth();
        final float containerWidth = Math.min(maxWidth, Math.max(dp(132), chromeWidth + textWidth));

        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);

        final float cardHeight = dp(25) * alpha;
        rect.set(
            (getWidth() - containerWidth) / 2f,
            dp(6),
            (getWidth() + containerWidth) / 2f,
            dp(6) + cardHeight
        );
        if (withShadows && SharedConfig.shadowsInSections) {
            backgroundPaint.setShadowLayer(dpf2(3), 0, dpf2(1), multAlpha(0x18000000, alpha));
        } else {
            backgroundPaint.setShadowLayer(0, 0, 0, 0);
        }
        int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha * .92f));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpf2(.7f));
        strokePaint.setColor(ColorUtils.setAlphaComponent(textColor, (int) (42 * alpha)));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint);

        clipPath.rewind();
        clipPath.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        final float cy = rect.centerY();
        final float controlRadius = dp(9.5f) * playBounce.getScale(.08f);
        final float controlCx = rect.left + dp(13.5f);
        playRect.set(controlCx - dp(12), cy - dp(12), controlCx + dp(12), cy + dp(12));
        controlPaint.setAlpha((int) (255 * alpha));
        canvas.drawCircle(controlCx, cy, controlRadius, controlPaint);

        controlIconPaint.setAlpha((int) (255 * alpha));
        if (currentTrack && playing) {
            canvas.drawLine(controlCx - dp(2.4f), cy - dp(3.4f), controlCx - dp(2.4f), cy + dp(3.4f), controlIconPaint);
            canvas.drawLine(controlCx + dp(2.4f), cy - dp(3.4f), controlCx + dp(2.4f), cy + dp(3.4f), controlIconPaint);
        } else {
            Path playPath = new Path();
            playPath.moveTo(controlCx - dp(2.2f), cy - dp(4));
            playPath.lineTo(controlCx + dp(4.1f), cy);
            playPath.lineTo(controlCx - dp(2.2f), cy + dp(4));
            playPath.close();
            canvas.drawPath(playPath, controlIconPaint);
        }

        float textX = rect.left + dp(28);
        this.author.draw(canvas, textX, cy, textColor, alpha);
        canvas.translate(this.author.getWidth(), 0);
        this.title.draw(canvas, textX, cy, textColor, 0.72f * alpha);
        canvas.translate(-this.author.getWidth(), 0);

        if (currentTrack) {
            float barsX = rect.right - dp(21);
            float phase = (SystemClock.uptimeMillis() % 900L) / 900f * (float) (Math.PI * 2);
            controlIconPaint.setColor(textColor);
            controlIconPaint.setStrokeWidth(dpf2(1.35f));
            controlIconPaint.setAlpha((int) (190 * alpha));
            for (int i = 0; i < 3; i++) {
                float bar = playing ? (.35f + .65f * Math.abs((float) Math.sin(phase + i * 1.55f))) : .35f + i * .15f;
                float half = dp(3.2f) * bar;
                canvas.drawLine(barsX + dp(i * 2.8f), cy - half, barsX + dp(i * 2.8f), cy + half, controlIconPaint);
            }
            controlIconPaint.setColor(controlIconColor);
        }

        arrowPaint.setStrokeWidth(dpf2(1.16f));
        canvas.translate(rect.right - dp(8), cy);
        canvas.drawPath(arrowPath, arrowPaint);

        if (currentTrack && playbackProgress > 0) {
            progressPaint.setAlpha((int) (210 * alpha));
            progressPaint.setStrokeWidth(dpf2(1.25f));
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(rect.left + dp(12), rect.bottom - dpf2(1.2f),
                    rect.left + dp(12) + (rect.width() - dp(24)) * playbackProgress,
                    rect.bottom - dpf2(1.2f), progressPaint);
        }

        canvas.restore();

        canvas.restore();

        if (currentTrack && playing) {
            postInvalidateOnAnimation();
        }
    }
}
