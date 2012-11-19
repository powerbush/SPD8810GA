package com.android.camera;

import android.util.Log;
import android.os.StatFs;

public class MemoryCheck{

    private static final String TAG = "MemoryCheck";

    private static final int DEFAULT_MAX_RATIO = 95;
    private static final String KEY_INTERNAL_STORAGE = "ro.internalstorage.threshold";

    public static boolean checkMemory() {
        boolean result = false;
        int max_ratio =
            android.os.SystemProperties.getInt(KEY_INTERNAL_STORAGE, DEFAULT_MAX_RATIO);
        String path = ImageManager.getBucketRootPath();
        StatFs stat = new StatFs(path);
        long total = (((long) stat.getBlockCount()) * ((long) stat.getBlockSize()));
        stat.restat(path);
        long free = (((long) stat.getAvailableBlocks()) * ((long) stat.getBlockSize()));
        float free_ratio = (((float) free) / ((float) total));
        float remain_ratio = (((float) (100 - max_ratio)) / 100);
        result = free_ratio < remain_ratio;
        Log.d(TAG,
            String.format("check memory total = %d, free = %d, free_ratio = %f, remain_ratio = %f, result = %b",
                new Object[] { total, free, free_ratio, remain_ratio, result }));
        return result;
    }
}