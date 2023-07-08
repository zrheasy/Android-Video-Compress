package com.zrh.video;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zrh
 * @date 2023/7/6
 */
public class VideoUtils {
    public static Map<Integer, MediaFormat> getTracks(MediaExtractor extractor) {
        Map<Integer, MediaFormat> map = new HashMap<>();
        int count = extractor.getTrackCount();
        for (int i = 0; i < count; i++) {
            map.put(i, extractor.getTrackFormat(i));
        }
        return map;
    }

    public static Map.Entry<Integer, MediaFormat> getTrack(Map<Integer, MediaFormat> map, String type) {
        for (Map.Entry<Integer, MediaFormat> entry : map.entrySet()) {
            String mimeType = entry.getValue().getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith(type)) {
                return entry;
            }
        }
        return null;
    }

    @WorkerThread
    public static VideoMetadata getMetadata(Context context,
                                            Uri uri) throws IllegalArgumentException, SecurityException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);

        return getMetadata(retriever);
    }

    @WorkerThread
    public static VideoMetadata getMetadata(File file) throws IllegalArgumentException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file.getAbsolutePath());

        return getMetadata(retriever);
    }

    public static VideoMetadata getMetadata(MediaMetadataRetriever retriever) {
        VideoMetadata metadata = new VideoMetadata();

        metadata.width = (int) getDouble(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        metadata.height = (int) getDouble(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        metadata.bitrate = getInt(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE);
        metadata.rotation = getInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        metadata.durationMs = getLong(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION);
        metadata.mimeType = getString(retriever, MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

        retriever.release();
        return metadata;
    }

    private static double getDouble(MediaMetadataRetriever retriever, int keycode) {
        String value = retriever.extractMetadata(keycode);
        if (value != null) {
            return Double.parseDouble(value);
        }
        return 0;
    }

    private static long getLong(MediaMetadataRetriever retriever, int keycode) {
        String value = retriever.extractMetadata(keycode);
        if (value != null) {
            return Long.parseLong(value);
        }
        return 0;
    }

    private static int getInt(MediaMetadataRetriever retriever, int keycode) {
        String value = retriever.extractMetadata(keycode);
        if (value != null) {
            return Integer.parseInt(value);
        }
        return 0;
    }

    private static String getString(MediaMetadataRetriever retriever, int keycode) {
        String value = retriever.extractMetadata(keycode);
        if (value != null) {
            return value;
        }
        return "";
    }
}
