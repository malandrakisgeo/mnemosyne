package com.gmalandrakis.mnemosyne.cache;


import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A specification of AbstractMnemosyneCache that includes a thread that periodically evicts the
 * expired or otherwise unnecessary Cache entries.
 * <p>
 * The fields declared here are explained in {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached}.
 * <p>
 * The build-in Cache algorithms provided by mnemosyne are implementations of AbstractGenericCache.
 * <p>
 */
public abstract class AbstractGenericCache<K, V> extends AbstractMnemosyneCache<K, V> {
    ExecutorService internalThreadService;
    ConcurrentMap<K, GenericCacheValue<V>> cachedValues;
    String name;
    boolean countdownFromCreation = false;
    long expirationTime;
    long invalidationInterval;
    int capacity;
    short capacityPercentageForEviction;
    short evictionStepPercentage;

    public AbstractGenericCache(CacheParameters parameters) {
        super();
        cachedValues = new ConcurrentHashMap<>();
        this.capacity = (parameters.getCapacity() <= 0 ? Integer.MAX_VALUE : parameters.getCapacity());
        this.expirationTime = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.invalidationInterval = (parameters.getInvalidationInterval() <= 0 ? Long.MAX_VALUE : parameters.getInvalidationInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
        this.capacityPercentageForEviction = (parameters.getPreemptiveEvictionPercentage() <= 0 || parameters.getPreemptiveEvictionPercentage() > 100 ? 100 : parameters.getPreemptiveEvictionPercentage());
        this.evictionStepPercentage =  (parameters.getEvictionStepPercentage() < 0 || parameters.getEvictionStepPercentage() > 100) ? 0 : parameters.getEvictionStepPercentage();
        if (parameters.getThreadPoolSize() != 0) {
            internalThreadService = Executors.newFixedThreadPool(parameters.getThreadPoolSize());
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
    public void remove(K key) {
        cachedValues.remove(key);
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
    }

    protected void forcedInvalidation() {
        while (true) {
            sleepUninterrupted(invalidationInterval);
            invalidateCache();
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

    protected void periodicallyEvict() {
        while (true) {
            if (cachedValues.size() >= capacity * (capacityPercentageForEviction / 100f)) {
                evict();
            }
            var sleepTime = Math.max(100 - Thread.activeCount(), 10);
            sleepUninterrupted(sleepTime);
        }
    }

    protected boolean isExpired(Map.Entry<K, GenericCacheValue<V>> entry) {
        long chosenVal = countdownFromCreation ? entry.getValue().getCreatedOn() : entry.getValue().getLastAccessed();
        return (System.currentTimeMillis() - chosenVal) > this.expirationTime;         //System.currentTimeMillis() is very slow on Linux though very fast on Windows, but System.nanoTime() the opposite. TODO: Utreda
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
                remainingSleep = remainingSleep - (System.currentTimeMillis() - sleepStarted);
            }
            if (remainingSleep <= 0) {
                sleepComplete = true;
            }
        }
    }


}
