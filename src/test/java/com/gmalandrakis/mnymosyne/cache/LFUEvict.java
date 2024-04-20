package com.gmalandrakis.mnymosyne.cache;

import com.gmalandrakis.mnemosyne.cache.LFUCache;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LFUEvict {
    private static final long cap = 1000000;

    @Test
    public void testLFU() {
        CacheParameters cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(cap);
        cacheParameters.setCacheName("test");
        LFUCache<Integer, String> lfu = new LFUCache<>(cacheParameters);
        var a = System.currentTimeMillis();
        fillWithShit(lfu);
        System.out.println("Time to fill: " + (System.currentTimeMillis() - a));
        a = System.currentTimeMillis();
        accessOneToFive(lfu);
        System.out.println("Time to access: " + (System.currentTimeMillis() - a));

        a = System.currentTimeMillis();
        lfu.evict();
        System.out.println("Time to Evict: " + (System.currentTimeMillis() - a));

    }


    public void fillWithShit(LFUCache cache) {
        for (int i = 0; i < cap; i++) {
            cache.put(i, String.valueOf(i));
        }
    }

    public void accessOneToFive(LFUCache cache) {
        ExecutorService executorService = Executors.newFixedThreadPool(15);
        for (int i = 0; i < cap; i++) {
            int finalI = i;
            executorService.submit(() -> randomizeAccesses(cache, finalI));
        }
    }

    private void randomizeAccesses(LFUCache cache, int i) {
        var rand = new Random();
        for (int j = 0; j < rand.nextInt(1, 10); j++) {
            var b = cache.get(i);
        }
    }
}
