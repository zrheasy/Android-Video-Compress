package com.zrh.video;

/**
 * @author zrh
 * @date 2023/7/8
 */
public interface VideoQualityStrategy {
    boolean accept(VideoQuality origin);
    VideoQuality calculate(VideoQuality origin);
}
