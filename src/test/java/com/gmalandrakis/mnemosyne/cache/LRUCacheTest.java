package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LRUCacheTest {
    /*
        Most of these test-cases are ChatGPT-generated, with minor modifications.
     */

    @Test
    public void testLRUCacheEviction() {
        CacheParameters params = new CacheParameters();
        params.setCapacity(3);
        ValuePool<Integer, String> val = new ValuePool<>();
        ProperLRUCache<Integer, Integer, String> cache = new ProperLRUCache<>(params, val);
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

    @Test
    public void testLRUCacheEvictions() {
        CacheParameters params = new CacheParameters();
        ValuePool<Integer, String> val = new ValuePool<>();
        LRUCache<Integer, Integer, testObject> cache = new LRUCache<>(params, val);

        var time = System.currentTimeMillis();

        for (int j = 0; j <= 50000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            cache.put(j, j, obj);
        }
        System.out.println(System.currentTimeMillis()-time);
        time = System.currentTimeMillis();
        ProperLRUCache<Integer, Integer, testObject> cache2 = new ProperLRUCache<>(params, val);
        for (int j = 0; j <= 50000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            cache2.put(j, j, obj);
        }
        System.out.println(System.currentTimeMillis()-time);

    }

    @Test
    public void testLinkedList() {
        List<testObject> linkedList = new LinkedList<testObject>();

        var objj = new testObject();
        objj.id = String.valueOf("-1");
        objj.name = "TEST ";
        linkedList.add(objj);

        var time = System.currentTimeMillis();
        var result = linkedList.contains(objj);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println(result);

        for (int j = 0; j <= 12000000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            linkedList.add(obj);
        }
        time = System.currentTimeMillis();
        var objjj = new testObject();

        objjj.id = String.valueOf(588);
        objjj.name = "TEST TESTSSO N"+"-588";
        result = linkedList.contains(objjj);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println(result);


    }

    @Test
    public void testConcurrent() {
        LinkedHashMap<testObject, Integer> linkedList = new LinkedHashMap<testObject, Integer>();

        var objj = new testObject();
        objj.id = String.valueOf("-1");
        objj.name = "TEST ";
        linkedList.put(objj, linkedList.size());

        var time = System.currentTimeMillis();
        var result = linkedList.containsKey(objj);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println(result);

        for (int j = 0; j <= 12000000; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            linkedList.put(objj, linkedList.size());
        }
        time = System.currentTimeMillis();
        var objjj = new testObject();

        objjj.id = String.valueOf(588);
        objjj.name = "TEST TESTSSO N"+"-588";
        result = linkedList.containsKey(objjj);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println(result);


    }

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

  /*  @Test
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
    }*/
}

