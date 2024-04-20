package com.gmalandrakis.mnemosyne.exception;

import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCacheSync;

import java.util.UUID;

public class crashTest {

    int totalItems = 150;
    FIFOCacheSync<Integer, String> fifoCacheSync;
    FIFOCache<Integer, String> fifoCacheConcurrent;
    public crashTest() {
      /*   fifoCacheSync = new FIFOCacheSync<>(100000, 1500000,
                200000000, "fifo-example");
        fifoCacheConcurrent = new FIFOCache<>(100000, 1500000,
                200000000, "fifo-example");

        var a = Executors.newFixedThreadPool(150);
        for (int i = 0; i < 20; i++) {
            int finalI1 = i;
            a.submit(() -> this.fifoexample(finalI1)).isDone();
            int finalI = i;
            a.submit(() -> this.fifoesyncxample(finalI)).isDone();
            System.out.println("");
        }*/

    }

    public void fifoesyncxample(int l) {
        long time = System.currentTimeMillis();

        for (int i = l; i < l+totalItems; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        var p = fifoCacheSync.getTargetKey();
        long time2 = System.currentTimeMillis();
        System.out.println("FIFOCacheSync: " + String.valueOf(time2 - time));

    }

    public void fifoexample(int l) {
        long time = System.currentTimeMillis();
        for (int i = l; i < l+totalItems; i++) {
            fifoCacheConcurrent.put(i, "String0" + i);
        }
        var p = fifoCacheConcurrent.getTargetKey();
        long time2 = System.currentTimeMillis();
        System.out.println("FIFOCacheConcurrent: " + String.valueOf(time2 - time));

    }

    public void params(int l,  String param, UUID uuid) {
        System.out.println("invoked!");

    }
}
