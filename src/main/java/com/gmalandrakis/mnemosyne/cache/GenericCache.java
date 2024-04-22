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

        if (getForcedInvalidationInterval() != 0) {
            internalThreadService = Executors.newFixedThreadPool(1);
            internalThreadService.submit(this::forcedInvalidation).isDone();
        }
    }

    @Override
    public void put(K key, V value) {
        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    @Override
    public V get(K key) {
        var val = cachedValues.get(key);
        return val != null ? val.get() : null; //TODO: Add value expiration check
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
    }

    private void forcedInvalidation() {
        var ms = this.getForcedInvalidationInterval();
        while (ms != 0) {
            sleepUninterrupted(ms);
            invalidateCache();
        }
    }

    /**
     * A dedicated thread (i.e. private in the cache) calls evict() periodically and sleeps for
     * a number of milliseconds that depends on the capacity and the current size of the cache
     * (i.e. the fuller the cache is, the more frequent the invocation of evict()).
     * <p>
     * The thread does not lock the Map when idle, and evict() can still be called when necessary.
     * <p>
     */
    protected void defaultCacheEvictionInternal() {
        long sleepTime;

        keepIdleWhenEmpty();
        this.evict();
        sleepTime = Math.min(capacity - cachedValues.size(), 1000); //TODO: Implement a smarter algorithm that takes into account the average time needed to evict.

        sleepUninterrupted(sleepTime);

    }

    /**
     * Forces the thread to sleep for a number of milliseconds, and suppresses the interrupts.
     * This is only meant to be used by internal threads whose sole purpose is to do something periodically.
     */
    private void sleepUninterrupted(long sleepTime) {
        boolean sleepComplete = false;
        long sleepStarted = 0;
        long remainingSleep;

        remainingSleep = sleepTime;
        while (!sleepComplete) {
            try {
                sleepStarted = System.currentTimeMillis();
                Thread.sleep(remainingSleep);
            } catch (InterruptedException e) {
                //oops!
            } finally {
                remainingSleep = remainingSleep - (System.currentTimeMillis() - sleepStarted); //
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
        } while (this.capacity != 0);
        System.out.println("stopped running");
    }
}
