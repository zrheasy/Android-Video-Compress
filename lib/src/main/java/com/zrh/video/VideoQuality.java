package com.zrh.video;

/**
 * @author zrh
 * @date 2023/7/8
 * <p>
 * 标清360P(480*360)
 * 高清480P(640×480)
 * 超清720P(1280×720)
 * 蓝光1080P(1920×1080)
 * <p>
 * 码率 =  (w x h x 3) * quality
 * <p>
 * 极低 quality = 1/4
 * 低 quality = 1/2
 * 中 quality = 1
 * 高 quality = 2
 * 极高 quality = 4
 */
public class VideoQuality {
    public static final float VERY_LOW = 0.25f;
    public static final float LOW = 0.5f;
    public static final float MEDIUM = 1;
    public static final float HIGH = 2;
    public static final float VERY_HIGH = 4;

    public static final int VIDEO_1080P = 1920;
    public static final int VIDEO_720P = 1280;
    public static final int VIDEO_480P = 640;
    public static final int VIDEO_360P = 480;

    // 0-width 1-height
    private final int[] resolution;
    private final int bitrate;

    public VideoQuality(int width, int height, int bitrate) {
        this.resolution = new int[]{width, height};
        this.bitrate = bitrate;
    }

    public VideoQuality(int[] resolution, int bitrate) {
        this.resolution = resolution;
        this.bitrate = bitrate;
    }

    public int getWidth() {
        return resolution[0];
    }

    public int getHeight() {
        return resolution[1];
    }

    public int[] getResolution() {
        return resolution;
    }

    public int getBitrate() {
        return bitrate;
    }
}
