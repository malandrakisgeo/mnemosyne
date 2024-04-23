package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LFUEvict {
    private static final long upperLimit = 100000; //I always looked for an opportunity to use this term in other contexts than math exams
    private ExecutorService executorService;
    private LFUCache<Long, String> lfu;

    @Before
    public void prepare() {
        CacheParameters cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(upperLimit / 2);
        cacheParameters.setCacheName("test");
        lfu = Mockito.spy(new LFUCache<>(cacheParameters));
    }

    @Test
    public void testLFU() throws InterruptedException {

        //LFUCache<Long, String> lfu = new LFUCache<>(cacheParameters);

        fillWithData(lfu, 0);
        assert (lfu.cachedValues.size() <= upperLimit / 2);
        verify(lfu, times(11)).evict();

        accessOneToFive(lfu);


    }

    @Test
    public void testAsync_noExceptions() {
        executorService = Executors.newFixedThreadPool(5);
        executorService.submit(() -> this.fillWithData(lfu, upperLimit)).isDone(); //assert no exceptions
        executorService.submit(lfu::evict).isDone(); //assert no exceptions
        executorService.submit(lfu::invalidateCache).isDone(); //assert no exceptions
        executorService.submit(() -> this.fillWithData(lfu, upperLimit)).isDone(); //assert no exceptions
        executorService.submit(lfu::invalidateCache).isDone(); //assert no exceptions
        assert(lfu.cachedValues.isEmpty());
    }


    private void fillWithData(LFUCache cache, long begin) {
        for (long i = begin; i < upperLimit + begin; i++) {
            cache.put(i, String.valueOf(i));
        }
    }

    private void accessOneToFive(LFUCache cache) {

        for (int i = 0; i < 5; i++) {
            int finalI = i;
            executorService.submit(() -> randomizeAccesses(cache, finalI));
        }
    }

    private void randomizeAccesses(LFUCache cache, int i) {
        var rand = new Random();
        for (int j = 0; j < rand.nextInt(1, 10); j++) {
            var b = cache.get(Long.valueOf(i));
        }
    }
}
