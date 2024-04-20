package com.gmalandrakis.mnemosyne.exception;

import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCacheSync;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

public class fifoexample {
    static int totalItems = 15000;

    public static void main(String[] args) {
        var rr = new crashTest();
        CacheParameters cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(10000);
        cacheParameters.setTimeToLive(1500000);
        cacheParameters.setInvalidationInterval(200000000);
        cacheParameters.setCacheName("fifo-example");
        fifoexample(cacheParameters);
        fifoesyncxample(cacheParameters); //sync example is a lot faster in a single-threaded environment (for 10000 capacity and 15000 writes, 280ms vs 25). But what about multithreaded?


    }

    public static void fifoesyncxample(CacheParameters cacheParameters) {
        long time = System.currentTimeMillis();


        FIFOCacheSync<Integer, String> fifoCacheSync = new FIFOCacheSync<>(cacheParameters);
        for (int i = 0; i < totalItems; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        var p = fifoCacheSync.getTargetKey();
        long time2 = System.currentTimeMillis();
        System.out.println("single threaded FIFOCacheSync: " + String.valueOf(time2 - time));


    }

    public static void fifoexample(CacheParameters cacheParameters) {
        long time = System.currentTimeMillis();
        FIFOCache<Integer, String> fifoCacheSync = new FIFOCache<>(cacheParameters);
        for (int i = 0; i < totalItems; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        var p = fifoCacheSync.getTargetKey();
        long time2 = System.currentTimeMillis();
        System.out.println("single threaded FIFOCacheConcurrent: " + String.valueOf(time2 - time));

    }
}
