package com.gmalandrakis.mnemosyne.cache;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LRUCacheTest {
    /*
        Most of these test-cases are ChatGPT-generated, with minor modifications.
     */

    @Test
    public void testLRUCacheEviction() {
        CacheParameters params = new CacheParameters();
        params.setCapacity(2);
        LRUCache<Integer, String> cache = new LRUCache<>(params);
        cache.put(1, "Value1");
        cache.put(2, "Value2");
        cache.put(3, "Value3"); // This should trigger eviction

        assertNull(cache.get(1)); // Evicted
        assertEquals("Value2", cache.get(2)); // Still in cache
        assertEquals("Value3", cache.get(3)); // Newly added
    }

    @Test
    public void testLRUCacheRemove() {
        LRUCache<Integer, String> cache = new LRUCache<>(new CacheParameters());
        cache.put(1, "Value1");
        cache.put(2, "Value2");
        cache.remove(1);

        assertNull(cache.get(1));
        assertEquals("Value2", cache.get(2));
    }

    @Test
    public void testLRUCacheExpirationAndInternalThreads() {
        CacheParameters params = new CacheParameters();
        params.setTimeToLive(300);
        params.setCapacity(10);
        params.setPreemptiveEvictionPercentage((short) 100);
        LRUCache<Integer, String> cache = new LRUCache<>(params);
        cache.put(1, "Value1");
        cache.put(2, "Value2");
        cache.put(4, "Value552");
        cache.put(5, "Valute2");
        cache.put(6, "Value2");

        try {
            Thread.sleep(500); //Value1 and Value2 will have been expired by the end of this.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        cache.put(3, "Value3");

        //verify(cache, atLeast(1)).evict();

        assertNull(cache.get(1));

        assertNull(cache.get(2));
        assertNotNull(cache.get(3));
    }

    @Test
    public void testLRUCacheConcurrency() throws InterruptedException {
        final LRUCache<Integer, String> cache = new LRUCache<>(new CacheParameters());
        final int numThreads = 10;
        final int numOperationsPerThread = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < numOperationsPerThread; j++) {
                    int key = (int) (Math.random() * 10);
                    cache.put(key, "Value" + key);
                    cache.get(key);
                    cache.remove(key);
                }
            });
        }

        //executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Check that the cache is empty after all threads finish
        assertEquals(0, cache.cachedValues.size());
    }
}

