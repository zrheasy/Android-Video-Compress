package com.zrh.video;

import static com.zrh.video.VideoQuality.HIGH;
import static com.zrh.video.VideoQuality.LOW;
import static com.zrh.video.VideoQuality.MEDIUM;
import static com.zrh.video.VideoQuality.VERY_HIGH;
import static com.zrh.video.VideoQuality.VIDEO_1080P;
import static com.zrh.video.VideoQuality.VIDEO_360P;
import static com.zrh.video.VideoQuality.VIDEO_480P;
import static com.zrh.video.VideoQuality.VIDEO_720P;

/**
 * @author zrh
 * @date 2023/7/8
 */
public class VideoQualityUtils {
    public static float getQuality(int[] resolution, int bitrate) {
        return bitrate * 1f / (resolution[0] * resolution[1] * 3);
    }

    public static float getLowerQuality(float quality) {
        if (quality >= VERY_HIGH) {
            quality = HIGH;
        } else if (quality >= HIGH) {
            quality = MEDIUM;
        } else if (quality >= MEDIUM) {
            quality = LOW;
        }
        return quality;
    }

    public static int getBitrate(int[] resolution, float quality) {
        return Math.round(resolution[0] * resolution[1] * 3 * quality);
    }

    public static int getResolutionLevel(int[] resolution) {
        if (resolution[0] >= VIDEO_1080P || resolution[1] >= VIDEO_1080P) {
            return VIDEO_1080P;
        }
        if (resolution[0] >= VIDEO_720P || resolution[1] >= VIDEO_720P) {
            return VIDEO_720P;
        }
        if (resolution[0] >= VIDEO_480P || resolution[1] >= VIDEO_480P) {
            return VIDEO_480P;
        }
        return VIDEO_360P;
    }

    public static int getLowerResolutionLevel(int level) {
        if (level >= VIDEO_1080P) {
            level = VIDEO_720P;
        } else if (level >= VIDEO_720P) {
            level = VIDEO_480P;
        } else if (level >= VIDEO_480P) {
            level = VIDEO_360P;
        }
        return level;
    }

    public static int[] getResolution(int[] resolution, int level) {
        int width = resolution[0];
        int height = resolution[1];
        int maxLength = Math.max(width, height);
        float scale = level * 1f / maxLength;

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        if (newWidth % 2 == 1) {
            newWidth += 1;
        }
        if (newHeight % 2 == 1) {
            newHeight += 1;
        }
        return new int[]{newWidth, newHeight};
    }
}
