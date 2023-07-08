package com.zrh.video;

import static android.media.MediaCodecList.REGULAR_CODECS;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.zrh.video.mp4.InputSurface;
import com.zrh.video.mp4.MP4Builder;
import com.zrh.video.mp4.Mp4Movie;
import com.zrh.video.mp4.OutputSurface;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import kotlin.jvm.Volatile;

/**
 * @author zrh
 * @date 2023/7/6
 */
class VideoCompressEngine implements Runnable {
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final long MEDIACODEC_TIMEOUT_US = 100L;

    private final ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VideoQualityStrategy strategy;
    private final File outputDir;
    private final String fileName;
    private File output;
    private final Source source;
    private final MediaExtractor extractor;
    @Volatile
    private boolean isRunning = false;

    private VideoCompressCallback mCallback;

    public VideoCompressEngine(
            ExecutorService executorService,
            File outputDir,
            String fileName,
            Source source
    ) {
        this.executorService = executorService;
        this.strategy = new LowQualityStrategy();
        this.outputDir = outputDir;
        this.fileName = fileName;
        this.source = source;
        this.extractor = new MediaExtractor();
    }

    public void setStrategy(VideoQualityStrategy strategy) {
        this.strategy = strategy;
    }

    public void setCallback(VideoCompressCallback callback) {
        this.mCallback = callback;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        executorService.submit(this);
    }

    public boolean cancel() {
        isRunning = false;
        mCallback = null;
        mainHandler.removeCallbacksAndMessages(null);
        return true;
    }

    private void notifyError(int code, String msg) {
        if (mCallback != null) {
            VideoCompressCallback callback = mCallback;
            mainHandler.post(() -> callback.onError(code, msg));
        }
        if (output.exists()) output.delete();
        VideoCompressUtils.remove(this);
    }

    private void notifyCompleted() {
        if (mCallback != null) {
            VideoCompressCallback callback = mCallback;
            mainHandler.post(() -> callback.onComplete(output));
        }
        VideoCompressUtils.remove(this);
    }

    private void notifyProgress(float percent) {
        if (mCallback != null) {
            VideoCompressCallback callback = mCallback;
            mainHandler.post(() -> callback.onProgress(percent));
        }
    }

    @Override
    public void run() {
        initOutput();
        VideoMetadata metadata;
        try {
            metadata = source.getMetadata();
            if (metadata.width == 0 || metadata.height == 0) {
                notifyError(VideoErrorCode.INVALID_SOURCE, "invalid source");
                return;
            }

            // 修正宽高
            if (metadata.rotation == 90 || metadata.rotation == 270) {
                int temp = metadata.width;
                metadata.width = metadata.height;
                metadata.height = temp;
            }
            metadata.rotation = 0;

            // 获取压缩的视频质量
            VideoQuality videoQuality = new VideoQuality(metadata.width, metadata.height, metadata.bitrate);

            // 未满足压缩条件则直接返回
            if (!strategy.accept(videoQuality)) {
                copySource();
                notifyCompleted();
                return;
            }
            videoQuality = strategy.calculate(videoQuality);
            metadata.bitrate = videoQuality.getBitrate();
            metadata.width = videoQuality.getResolution()[0];
            metadata.height = videoQuality.getResolution()[1];

            // 设置视频源
            source.setup(extractor);
            compress(metadata, videoQuality);
        } catch (Exception e) {
            e.printStackTrace();
            notifyError(VideoErrorCode.SOURCE_NOT_FOUND, "error:" + e);
        }
    }

    private void compress(VideoMetadata metadata, VideoQuality videoQuality) {
        try {
            Mp4Movie mp4Movie = new Mp4Movie();
            mp4Movie.setCacheFile(output);
            mp4Movie.setRotation(metadata.rotation);
            MP4Builder mediaMuxer = new MP4Builder().createMovie(mp4Movie);

            Map<Integer, MediaFormat> tracks = VideoUtils.getTracks(extractor);
            boolean success = processVideo(videoQuality, metadata.durationMs, tracks, mediaMuxer);
            if (success) {
                success = processAudio(tracks, mediaMuxer);
            }

            extractor.release();
            mediaMuxer.finishMovie(!success);
            if (success) {
                notifyCompleted();
            }
        } catch (Exception e) {
            e.printStackTrace();
            notifyError(VideoErrorCode.ERROR, "error: " + e);
        }
    }

    private boolean processAudio(Map<Integer, MediaFormat> tracks, MP4Builder mediaMuxer) {
        Map.Entry<Integer, MediaFormat> audioTrack = VideoUtils.getTrack(tracks, "audio/");
        if (audioTrack == null) {
            return true;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int audioIndex = audioTrack.getKey();
        extractor.selectTrack(audioIndex);

        try {
            MediaFormat audioFormat = audioTrack.getValue();
            int trackIndex = mediaMuxer.addTrack(audioFormat, true);
            int maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if (maxBufferSize <= 0) {
                maxBufferSize = 64 * 1024;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                long size = extractor.getSampleSize();
                if (size > maxBufferSize) {
                    maxBufferSize = (int) (size + 1024);
                }
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);

            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            boolean inputDone = false;
            while (isRunning && !inputDone) {
                int index = extractor.getSampleTrackIndex();
                if (index == audioIndex) {
                    bufferInfo.size = extractor.readSampleData(buffer, 0);
                    if (bufferInfo.size >= 0) {
                        bufferInfo.presentationTimeUs = extractor.getSampleTime();
                        bufferInfo.offset = 0;
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        mediaMuxer.writeSampleData(trackIndex, buffer, bufferInfo, true);
                        extractor.advance();
                    } else {
                        inputDone = true;
                    }
                } else if (index == -1) {
                    inputDone = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            notifyError(VideoErrorCode.ERROR, "muxer audio error");
            return false;
        } finally {
            extractor.unselectTrack(audioIndex);
        }

        return isRunning;
    }

    private boolean processVideo(VideoQuality quality,
                                 long durationMs,
                                 Map<Integer, MediaFormat> tracks,
                                 MP4Builder mediaMuxer) {
        Map.Entry<Integer, MediaFormat> videoTrack = VideoUtils.getTrack(tracks, "video/");
        if (videoTrack == null) {
            notifyError(VideoErrorCode.VIDEO_TRACK_NOT_FOUND, "video track not found");
            return false;
        }
        // 选中视频轨
        int videoIndex = videoTrack.getKey();
        extractor.selectTrack(videoIndex);
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        // 设置输出参数
        MediaFormat inputFormat = videoTrack.getValue();
        MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, quality.getWidth(), quality.getHeight());
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, quality.getBitrate());
        outputFormat.setLong(MediaFormat.KEY_DURATION, durationMs * 1000);
        setupOutputFormat(inputFormat, outputFormat);
        // 初始化编码器
        MediaCodec encoder = prepareEncoder(outputFormat);
        if (encoder == null) {
            notifyError(VideoErrorCode.ENCODER_NOT_FOUND, "encoder not found");
            return false;
        }
        InputSurface inputSurface;
        try {
            inputSurface = new InputSurface(encoder.createInputSurface());
        } catch (Exception e) {
            e.printStackTrace();
            encoder.release();
            notifyError(VideoErrorCode.ERROR, "encode error:" + e);
            return false;
        }
        // 初始化解码器
        OutputSurface outputSurface = null;
        MediaCodec decoder = null;

        try {
            inputSurface.makeCurrent();
            encoder.start();

            outputSurface = new OutputSurface();
            decoder = prepareDecoder(inputFormat, outputSurface);
            if (decoder == null) {
                notifyError(VideoErrorCode.DECODER_NOT_FOUND, "decoder not found");
                return false;
            }
            decoder.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int videoTrackIndex = -5;

            while (!outputDone) {
                if (!inputDone) {
                    int sampleIndex = extractor.getSampleTrackIndex();
                    if (sampleIndex == videoIndex) {
                        int inputBufferIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_US);
                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                            int chunkSize = extractor.readSampleData(inputBuffer, 0);
                            if (chunkSize >= 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            }
                        }
                    } else if (sampleIndex == -1) {
                        int inputBufferIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_US);
                        if (inputBufferIndex >= 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        }
                    }
                }

                boolean decoderOutputAvailable = true;
                boolean encoderOutputAvailable = true;
                while (isRunning && (decoderOutputAvailable || encoderOutputAvailable)) {
                    // handle encoder
                    int encodeIndex = encoder.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIMEOUT_US);
                    if (encodeIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderOutputAvailable = false;
                    } else if (encodeIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (videoTrackIndex == -5) {
                            videoTrackIndex = mediaMuxer.addTrack(encoder.getOutputFormat(), false);
                        }
                    } else if (encodeIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (encodeIndex < 0) {
                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encodeIndex);
                    } else {
                        ByteBuffer encodeData = encoder.getOutputBuffer(encodeIndex);
                        if (bufferInfo.size > 1 && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            mediaMuxer.writeSampleData(videoTrackIndex, encodeData, bufferInfo, false);
                        }
                        outputDone = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encodeIndex, false);
                    }
                    if (encodeIndex != MediaCodec.INFO_TRY_AGAIN_LATER) continue;

                    // handle decoder
                    int decodeIndex = decoder.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIMEOUT_US);
                    if (decodeIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderOutputAvailable = false;
                    } else if (decodeIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    } else if (decodeIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    } else if (decodeIndex < 0) {
                        throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + encodeIndex);
                    } else {
                        boolean doRender = bufferInfo.size != 0;
                        decoder.releaseOutputBuffer(decodeIndex, doRender);
                        if (doRender) {
                            try {
                                outputSurface.awaitNewImage();
                                outputSurface.drawImage(false);
                                inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000);
                                notifyProgress((bufferInfo.presentationTimeUs / 1000f) / durationMs * 100);
                                inputSurface.swapBuffers();
                            } catch (Exception ignored) {}
                        }
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderOutputAvailable = false;
                            encoder.signalEndOfInputStream();
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            notifyError(VideoErrorCode.ERROR, "encode error:" + e);
            return false;
        } finally {
            extractor.unselectTrack(videoTrack.getKey());
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            encoder.stop();
            encoder.release();
            outputSurface.release();
            inputSurface.release();
        }

        return isRunning;
    }

    private MediaCodec prepareDecoder(MediaFormat inputFormat, OutputSurface outputSurface) {
        MediaCodec decoder = null;
        try {
            decoder = findCodec(false, inputFormat);
            if (decoder != null) {
                decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }
        }

        return decoder;
    }

    private MediaCodec prepareEncoder(MediaFormat outputFormat) {
        MediaCodec encoder = null;
        try {
            encoder = findCodec(true, outputFormat);
            if (encoder != null) {
                encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (encoder != null) {
                encoder.release();
                encoder = null;
            }
        }

        return encoder;
    }

    private MediaCodec findCodec(boolean isEncoder, MediaFormat format) throws IOException {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        Map<String, MediaCodecInfo.CodecCapabilities> codecList = findSupportCodec(isEncoder, mimeType);
        String encoderName = null;
        for (String name : codecList.keySet()) {
            MediaCodecInfo.CodecCapabilities capabilities = codecList.get(name);
            if (capabilities.isFormatSupported(format)) {
                encoderName = name;
                break;
            }
        }
        if (encoderName != null) {
            MediaCodec encoder = MediaCodec.createByCodecName(encoderName);
            return encoder;
        }
        return null;
    }

    private void setupOutputFormat(MediaFormat inputFormat, MediaFormat outputFormat) {
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        setOutputInteger(inputFormat, outputFormat, MediaFormat.KEY_FRAME_RATE, 30);
        setOutputInteger(inputFormat, outputFormat, MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setOutputInteger(inputFormat, outputFormat, MediaFormat.KEY_COLOR_STANDARD, null);
            setOutputInteger(inputFormat, outputFormat, MediaFormat.KEY_COLOR_TRANSFER, null);
            setOutputInteger(inputFormat, outputFormat, MediaFormat.KEY_COLOR_RANGE, null);
        }
    }

    private Map<String, MediaCodecInfo.CodecCapabilities> findSupportCodec(boolean isEncoder, String type) {
        MediaCodecList mediaCodecList = new MediaCodecList(REGULAR_CODECS);
        Map<String, MediaCodecInfo.CodecCapabilities> map = new HashMap<>();
        for (MediaCodecInfo codecInfo : mediaCodecList.getCodecInfos()) {
            if (codecInfo.isEncoder() == isEncoder) {
                try {
                    MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(type);
                    map.put(codecInfo.getName(), capabilities);
                } catch (Exception ignored) {

                }
            }
        }
        return map;
    }

    private void setOutputInteger(MediaFormat inputFormat, MediaFormat outputFormat, String key, Integer defValue) {
        if (inputFormat.containsKey(key)) {
            outputFormat.setInteger(key, inputFormat.getInteger(key));
        } else if (defValue != null) {
            outputFormat.setInteger(key, defValue);
        }
    }

    private void copySource() throws IOException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = source.getInputStream();
            outputStream = new FileOutputStream(output);
            int len;
            byte[] buff = new byte[1024];
            while ((len = inputStream.read(buff)) != -1) {
                outputStream.write(buff, 0, len);
            }
            outputStream.flush();
        } finally {
            closeStream(inputStream);
            closeStream(outputStream);
        }
    }

    private void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {}
        }
    }

    private void initOutput() {
        if (!outputDir.exists()) outputDir.mkdirs();
        output = new File(outputDir, fileName);
    }

    interface Source {
        void setup(@NonNull MediaExtractor extractor) throws IOException;

        @NonNull
        VideoMetadata getMetadata();

        InputStream getInputStream() throws IOException;
    }

    static class FileSource implements Source {
        private final File file;

        public FileSource(File file) {
            this.file = file;
        }

        @Override
        public void setup(@NonNull MediaExtractor extractor) throws IOException {
            extractor.setDataSource(file.getAbsolutePath());
        }

        @NonNull
        @Override
        public VideoMetadata getMetadata() {
            return VideoUtils.getMetadata(file);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }
    }

    static class UriSource implements Source {

        private final Context context;
        private final Uri uri;

        public UriSource(Context context, Uri uri) {
            this.context = context.getApplicationContext();
            this.uri = uri;
        }

        @Override
        public void setup(@NonNull MediaExtractor extractor) throws IOException {
            extractor.setDataSource(context, uri, null);
        }

        @NonNull
        @Override
        public VideoMetadata getMetadata() {
            return VideoUtils.getMetadata(context, uri);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return context.getContentResolver().openInputStream(uri);
        }
    }
}
