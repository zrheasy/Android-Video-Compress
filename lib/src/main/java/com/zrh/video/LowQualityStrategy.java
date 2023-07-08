package com.zrh.video;

/**
 * @author zrh
 * @date 2023/7/8
 */
public class LowQualityStrategy implements VideoQualityStrategy {
    @Override
    public boolean accept(VideoQuality origin) {
        int level = VideoQualityUtils.getResolutionLevel(origin.getResolution());
        float quality = VideoQualityUtils.getQuality(origin.getResolution(), origin.getBitrate());
        return level > VideoQuality.VIDEO_360P || !(quality <= VideoQuality.LOW);
    }

    @Override
    public VideoQuality calculate(VideoQuality origin) {
        //根据分辨率决定视频的清晰度
        int level = VideoQualityUtils.getResolutionLevel(origin.getResolution());
        level = VideoQualityUtils.getLowerResolutionLevel(level);
        int[] resolution = VideoQualityUtils.getResolution(origin.getResolution(), level);

        float quality = VideoQualityUtils.getQuality(origin.getResolution(), origin.getBitrate());
        quality = VideoQualityUtils.getLowerQuality(quality);
        int bitrate = VideoQualityUtils.getBitrate(resolution, quality);
        return new VideoQuality(resolution, bitrate);
    }
}
