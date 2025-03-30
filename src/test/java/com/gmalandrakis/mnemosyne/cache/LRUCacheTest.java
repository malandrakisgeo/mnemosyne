package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class LRUCacheTest {
    /*
        Most of these test-cases are ChatGPT-generated, with minor modifications.
     */

    @Test
    public void testLRUCacheEviction() {
        CacheParameters params = new CacheParameters();
        params.setCapacity(3);
        ValuePool<Integer, String> val = new ValuePool<>();
        LRUCache<Integer, Integer, String> cache = new LRUCache<>(params, val);
        cache.put(1, 1, "Value1");
        cache.put(2, 2, "Value2");
        cache.put(3, 3, "Value3");
        cache.put(4, 4, "Value4"); // This should trigger eviction

        assertNull(cache.get(1)); // Evicted
        assertEquals("Value2", cache.get(2)); // Still in cache
        assertEquals("Value3", cache.get(3)); // Newly added
        assertEquals("Value4", cache.get(4)); // Newly added
        assert (val.getNumberOfUsesForId(1) == 0);


        cache.invalidateCache();
        cache.put(1, 1, "Value1");
        cache.put(2, 2, "Value2");
        cache.put(3, 3, "Value3");
        cache.get(1); //Requested!
        cache.put(4, 4, "Value4"); // This should trigger eviction

        assertNull(cache.get(2)); // Evicted
        assertEquals("Value1", cache.get(1)); // Requested, still in cache
        assertEquals("Value3", cache.get(3)); // Newly added
        assertEquals("Value4", cache.get(4)); // Newly added
        assert (val.getNumberOfUsesForId(1) == 1);
    }

    /*@Test
    public void testLRUCacheEvictions() {
        CacheParameters params = new CacheParameters();
        ValuePool<Integer, String> val = new ValuePool<>();
        LRUCacheOld<Integer, Integer, testObject> cache = new LRUCacheOld<>(params, val);

        var time = System.currentTimeMillis();

        for (int j = 0; j <= 50000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            cache.put(j, j, obj);
        }
        System.out.println(System.currentTimeMillis()-time);
        time = System.currentTimeMillis();
        LRUCache<Integer, Integer, testObject> cache2 = new LRUCache<>(params, val);
        for (int j = 0; j <= 50000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            cache2.put(j, j, obj);
        }
        System.out.println(System.currentTimeMillis()-time);

    }*/



    class testObject {
        String id;
        String name;
        String email;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FIFOTest.testObject object)) return false;
            return Objects.equals(id, object.id) && Objects.equals(name, object.name) && Objects.equals(email, object.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, email);
        }
    }

    @Test
    public void testLRUCacheRemove() {
        ValuePool<Integer, String> val = new ValuePool<>();

        LRUCache<Integer, Integer, String> cache = new LRUCache<>(new CacheParameters(), val);

        cache.put(1, 1,"Value1");
        cache.put(2, 2,"Value2");
        cache.remove(1);

        assertNull(cache.get(1));
        assertEquals("Value2", cache.get(2));
    }

    @Test
    public void testLRUCacheExpirationAndInternalThreads() {
        CacheParameters params = new CacheParameters();
        params.setTimeToLive(200);
        params.setCapacity(10);
        params.setPreemptiveEvictionPercentage((short) 100);
        ValuePool<Integer, String> val = new ValuePool<>();

        LRUCache<Integer, Integer, String> cache = new LRUCache<>(params, val);
        cache.put(1, 1,"Value1");
        cache.put(2,2, "Value2");

        try {
            Thread.sleep(300); //Value1 and Value2 will have been expired by the end of this.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        cache.put(3,3, "Value3");

        assertNull(cache.get(1));
        assertNull(cache.get(2));
        assertNotNull(cache.get(3));
    }

    @Test
    public void testLRUCacheConcurrency() throws InterruptedException {
        ValuePool<Integer, String> val = new ValuePool<>();
        final LRUCache<Integer, Integer, String> cache = new LRUCache<>(new CacheParameters(), val);

        final int numThreads = 10;
        final int numOperationsPerThread = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < numOperationsPerThread; j++) {
                    int key = (int) (Math.random() * 10);
                    cache.put(key, key,"Value" + key);
                    cache.get(key);
                    cache.remove(key);
                }
            });
        }

        //executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Check that the cache is empty after all threads finish
        assertEquals(0, cache.getKeyIdMapper().size());
    }
}

