package com.zrh.video;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * @author zrh
 * @date 2023/7/6
 */
public interface VideoCompressCallback {
    void onComplete(@NonNull File output);

    void onProgress(float percent);

    void onError(int code, @NonNull String msg);
}
