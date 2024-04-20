package com.gmalandrakis.mnemosyne.cache;


import com.gmalandrakis.mnemosyne.core.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A specification of AbstractCache that includes a thread that periodically evicts the expired or otherwise unnecessary Cache entries.
 * <p>
 * The build-in Cache algorithms provided by mnemosyne are implementations of GenericCache.
 * <p>
 */
abstract class GenericCache<K, V> extends AbstractCache<K, V> {
    ExecutorService internalThreadService;
    ConcurrentMap<K, GenericCacheValue<V>> cachedValues;


    public GenericCache(CacheParameters cacheParameters) {
        super(cacheParameters);

        if (capacity != 0 || expirationTime != 0) {
            internalThreadService = Executors.newFixedThreadPool(1);
            internalThreadService.submit(this::defaultCacheEviction).isDone();
        }
    }

    @Override
    public void put(K key, V value) {
        // this.cachedValues.put(key, value);
    }

    @Override
    public V get(K key) {
        var val = cachedValues.get(key);
        return val != null ? val.get() : null; //TODO: Add value expiration check
    }

    @Override
    public void evictAll() {
        var keySet = cachedValues.keySet();
        for (var key : keySet) {
            cachedValues.remove(key); //TODO: Test
        }
    }

    /**
     * A dedicated thread (i.e. private in the cache) calls evict() periodically and sleeps for
     * a number of milliseconds that depends on the capacity and the current size of the cache
     * (i.e. the fuller the cache is, the more frequent the invocation of evict()).
     * <p>
     * The thread does not lock the Map when idle, and evict() can still be called when necessary.
     * <p>
     * The thread will normally not be interrupted, unless the JVM wants it to.
     * If interrupted, the InterruptedException is suppressed and the sleep continues for the remaining time.
     */
    private void defaultCacheEvictionInternal() {
        long sleepStarted = 0;
        long remainingSleep;
        long sleepTime;
        boolean sleepComplete = false;

        keepIdleWhenEmpty();
        evict();

        sleepTime = Math.max(capacity - cachedValues.size(), 1000000000); //TODO: Implement a smarter algorithm that takes into account the average time needed to evict.
        remainingSleep = sleepTime;

        while (!sleepComplete) {
            try {
                sleepStarted = System.currentTimeMillis();
                System.out.println("in the thread!");
                Thread.sleep(remainingSleep);
            } catch (InterruptedException e) {
                //oops!
            } finally {
                remainingSleep = remainingSleep - (System.currentTimeMillis() - sleepStarted);
            }
            if (remainingSleep <= 0) {
                sleepComplete = true;
            }
        }

    }

    private void keepIdleWhenEmpty() {
        while (cachedValues.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                //ignore
            }
        }
    }

    protected void defaultCacheEviction() {
        do {
            this.defaultCacheEvictionInternal();
        } while (capacity != 0);

    }
}
