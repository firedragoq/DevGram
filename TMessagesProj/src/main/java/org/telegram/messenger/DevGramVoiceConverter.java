package org.telegram.messenger;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// DevGram: декодер голосового сообщения (ogg/opus и т.п.) в PCM для Vosk — порт FormatConverter exteraGram.
// Чистый Android MediaCodec/MediaExtractor, без нативных зависимостей. Ленивый InputStream отдаёт PCM по мере декодирования.
public abstract class DevGramVoiceConverter {

    // Частота дискретизации аудиодорожки файла (для создания Vosk-Recognizer). По умолчанию 48000.
    public static int getSampleRate(String path) {
        MediaExtractor extractor = new MediaExtractor();
        int result = -1;
        try {
            extractor.setDataSource(path);
            int tracks = extractor.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString("mime");
                if (mime != null && mime.startsWith("audio/") && format.containsKey("sample-rate")) {
                    result = format.getInteger("sample-rate");
                    break;
                }
            }
        } catch (IOException e) {
            FileLog.e(e);
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
        }
        return result == -1 ? 48000 : result;
    }

    public static InputStream extractAndConvertToPcm(String path, boolean limitToOneMinute) throws IOException {
        return new LazyPcmInputStream(path, limitToOneMinute);
    }

    // Ленивый поток PCM: декодирует аудио порциями через MediaCodec.
    public static class LazyPcmInputStream extends InputStream {
        private static final long ONE_MINUTE_US = 60_000_000L;

        private final MediaCodec codec;
        private final MediaExtractor extractor;
        private final boolean limitToOneMinute;
        private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        private ByteBuffer currentOutputBuffer;
        private boolean isEOS;
        private long totalDecodedDurationUs;

        public LazyPcmInputStream(String path, boolean limitToOneMinute) throws IOException {
            this.limitToOneMinute = limitToOneMinute;
            extractor = new MediaExtractor();
            extractor.setDataSource(path);
            MediaFormat trackFormat = extractor.getTrackFormat(0);
            String mime = trackFormat.getString("mime");
            if (mime == null || !mime.startsWith("audio/")) {
                throw new IOException("Not an audio file");
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(trackFormat, null, null, 0);
            codec.start();
            extractor.selectTrack(0);
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            if (read(one, 0, 1) == -1) {
                return -1;
            }
            return one[0] & 0xFF;
        }

        @Override
        public int read(byte[] out, int offset, int length) {
            if (isEOS) {
                return -1;
            }
            int written = 0;
            while (written < length && !isEOS) {
                if (currentOutputBuffer == null || !currentOutputBuffer.hasRemaining()) {
                    currentOutputBuffer = nextOutputBuffer();
                    if (currentOutputBuffer == null) {
                        break;
                    }
                }
                int chunk = Math.min(length - written, currentOutputBuffer.remaining());
                currentOutputBuffer.get(out, offset + written, chunk);
                written += chunk;
            }
            return written > 0 ? written : -1;
        }

        private ByteBuffer nextOutputBuffer() {
            while (!isEOS) {
                int inIndex = codec.dequeueInputBuffer(10_000L);
                if (inIndex >= 0) {
                    ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                    int size = inBuf != null ? extractor.readSampleData(inBuf, 0) : -1;
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L);
                if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    ByteBuffer copy = ByteBuffer.allocate(bufferInfo.size);
                    if (outBuf != null) {
                        copy.put(outBuf);
                    }
                    copy.flip();
                    codec.releaseOutputBuffer(outIndex, false);
                    totalDecodedDurationUs = bufferInfo.presentationTimeUs;
                    if (limitToOneMinute && totalDecodedDurationUs >= ONE_MINUTE_US) {
                        isEOS = true;
                    }
                    return copy;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            try {
                codec.stop();
                codec.release();
            } catch (Throwable ignore) {
            }
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
            super.close();
        }
    }
}
