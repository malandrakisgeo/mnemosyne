package com.gmalandrakis.mnemosyne.cache;


import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A specification of AbstractCache that includes a thread that periodically evicts the expired or otherwise unnecessary Cache entries.
 * <p>
 * The build-in Cache algorithms provided by mnemosyne are implementations of AbstractGenericCache.
 * <p>
 */
public abstract class AbstractGenericCache<K, V> extends AbstractCache<K, V> {
    ExecutorService internalThreadService;
    ConcurrentMap<K, GenericCacheValue<V>> cachedValues;


    public AbstractGenericCache(CacheParameters cacheParameters) {
        super(cacheParameters);
        if (cacheParameters.getPreemptiveEvictionPercentage() == 0) {
            this.capacityPercentageForEviction = 80;
        }
        if (cacheParameters.getThreadPoolSize() != 0) {
            internalThreadService = Executors.newFixedThreadPool(cacheParameters.getThreadPoolSize());
        } else {
            internalThreadService = Executors.newCachedThreadPool();
        }
        setInternalThreads();
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

    protected void forcedInvalidation() {
        var ms = this.invalidationInterval;
        while (ms != 0) {
            sleepUninterrupted(ms);
            invalidateCache();
        }
    }

    protected void periodicallyEvict() {
        while (true) {
            if (cachedValues.size() >= capacity * (capacityPercentageForEviction / 100)) {
                evict();
            }
            var sleepTime = Math.max(100 - Thread.activeCount(), 1);
            sleepUninterrupted(sleepTime);
        }
    }

    /**
     * Puts the thread to sleep for a number of milliseconds, and suppresses the interrupts.
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

    protected void setInternalThreads() {
        if (invalidationInterval != Long.MAX_VALUE) {
            internalThreadService.execute(this::forcedInvalidation);
        }
        if (capacity != Integer.MAX_VALUE) {
            internalThreadService.execute(this::periodicallyEvict);
        }
    }


}
