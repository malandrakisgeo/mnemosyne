package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FIFOTest {

    private final int totalItems = 15000;

    private CacheParameters cacheParameters;
    @Before
    public void test() {
        cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(totalItems-1);
        cacheParameters.setTimeToLive(1500000);
        cacheParameters.setForcedEvictionInterval(200000000);
        cacheParameters.setCacheName("fifo-example");

    }

    @Test
    public void fifoesyncxample() {  //sync example is a lot faster in a single-threaded environment (for 10000 capacity and 15000 writes, 280ms vs 25ms on my machine). But what about multithreaded?

        FIFOCacheSync<Integer, String> fifoCacheSync = new FIFOCacheSync<>(cacheParameters);
        for (int i = 0; i < totalItems; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        var p = fifoCacheSync.getTargetKey();
        assert (fifoCacheSync.get(p).equals("String0"+String.valueOf(totalItems-1)));
    }

    @Test
    public void fifoexample() { //assert no exceptions
        long time = System.currentTimeMillis();
        FIFOCache<Integer, String> fifoCacheSync = new FIFOCache<>(cacheParameters);
        for (int i = 0; i < totalItems; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        var p = fifoCacheSync.getTargetKey();
        assert (fifoCacheSync.cachedValues.size()<=totalItems-1);
        long time2 = System.currentTimeMillis();
        System.out.println("single threaded FIFOCacheConcurrent: " + String.valueOf(time2 - time));
    }

    @Test
    public void fifoexample_multithreaded() throws Exception { //assert no exceptions
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        int capacityExceededBy = 5;
        long time = System.currentTimeMillis();
        FIFOCache<Integer, String> fifoCacheSync = new FIFOCache<>(cacheParameters);
       for (int i = 0; i <= 1000; i++) {
            fifoCacheSync.put(i, "String0" + i);
        }
        executorService.submit(()-> this.fillWithItems(1001, 10000, fifoCacheSync)).isDone();
        executorService.submit(()-> this.fillWithItems(10001, totalItems+capacityExceededBy, fifoCacheSync)).isDone();
        Thread.sleep(1500); //TODO: Improve this
        var nextToBeEvicted = fifoCacheSync.getTargetKey();

        assert (fifoCacheSync.get(nextToBeEvicted).equals("String0"+ capacityExceededBy));

    }

    private void fillWithItems(int start, int end, FIFOCache fifoCache){
        for (int i = start; i < end; i++) {
            fifoCache.put(i, "String0" + i);
        }
    }
}
