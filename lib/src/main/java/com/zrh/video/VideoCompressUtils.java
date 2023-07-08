package com.zrh.video;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zrh
 * @date 2023/7/6
 */
public class VideoCompressUtils {
    private static final AtomicInteger idAtomic = new AtomicInteger(0);
    private static final Map<Integer, VideoCompressEngine> runningEngines = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * @return 返回压缩任务的id
     */
    public static int compress(
            Context context,
            Uri src,
            File outputDir,
            String fileName,
            VideoCompressCallback callback) {
        return compress(new VideoCompressEngine.UriSource(context, src), outputDir, fileName, callback);
    }

    /**
     * @return 返回压缩任务的id
     */
    public static int compress(
            File input,
            File outputDir,
            String fileName,
            VideoCompressCallback callback) {
        return compress(new VideoCompressEngine.FileSource(input), outputDir, fileName, callback);
    }

    private static int compress(
            VideoCompressEngine.Source source,
            File outputDir,
            String fileName,
            VideoCompressCallback callback) {
        int id = idAtomic.getAndIncrement();
        VideoCompressEngine engine = new VideoCompressEngine(executor, outputDir, fileName, source);
        engine.setCallback(callback);
        engine.start();
        runningEngines.put(id, engine);
        return id;
    }

    public static boolean cancel(int id) {
        VideoCompressEngine engine = runningEngines.remove(id);
        if (engine == null) return false;
        return engine.cancel();
    }

    static void remove(VideoCompressEngine engine) {
        for (Integer id : runningEngines.keySet()) {
            if (runningEngines.get(id) == engine) {
                runningEngines.remove(id);
                break;
            }
        }
    }
}
