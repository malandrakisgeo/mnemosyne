package com.gmalandrakis.mnemosyne.cache;


import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A specification of AbstractMnemosyneCache that includes threads that periodically evict the
 * expired or otherwise unnecessary Cache entries.
 * <p>
 * The fields declared here are explained in {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached}.
 * <p>
 * The build-in Cache algorithms provided by mnemosyne are implementations of AbstractGenericCache.
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the cached value.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public abstract class AbstractGenericCache<K, V> extends AbstractMnemosyneCache<K, V> {
    final ExecutorService internalThreadService;
    ConcurrentMap<K, GenericCacheValue<V>> cachedValues;
    final String name;
    final boolean countdownFromCreation;
    final long timeToLive;
    final long invalidationInterval;
    final int totalCapacity;
    final float actualCapacity;
    final short preemptiveEvictionPercentage;
    final short evictionStepPercentage;

    public AbstractGenericCache(CacheParameters parameters) {
        super();
        cachedValues = new ConcurrentHashMap<>();
        this.totalCapacity = (parameters.getCapacity() <= 0 ? Integer.MAX_VALUE : parameters.getCapacity());
        this.timeToLive = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.invalidationInterval = (parameters.getInvalidationInterval() < 0 ? Long.MAX_VALUE : parameters.getInvalidationInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
        this.preemptiveEvictionPercentage = (parameters.getPreemptiveEvictionPercentage() <= 0 || parameters.getPreemptiveEvictionPercentage() >= 100 ? 100 : parameters.getPreemptiveEvictionPercentage());
        this.evictionStepPercentage =  (parameters.getEvictionStepPercentage() < 0 || parameters.getEvictionStepPercentage() > 100) ? 0 : parameters.getEvictionStepPercentage();
        this.actualCapacity = (totalCapacity * (preemptiveEvictionPercentage / 100f));
        if (parameters.getThreadPoolSize() != 0) {
            internalThreadService = Executors.newFixedThreadPool(parameters.getThreadPoolSize());
        } else {
            internalThreadService = Executors.newCachedThreadPool();
        }
        setInternalThreads();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(K key, V value) {
        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(K key) {
        var val = cachedValues.get(key);
        return val != null ? val.get() : null; //TODO: Add value expiration check
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(K key) {
        cachedValues.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateCache() {
        cachedValues.clear();
    }

    /**
     * Forcibly invalidates the cache at regular intervals, depending on the presence or absence of an invalidationInterval parameter in the cache.
     */
    protected void forcedInvalidation() {
        while (true) {
            sleepUninterrupted(invalidationInterval);
            invalidateCache();
        }
    }

    /**
     * Initializes the internal threads depending on the timeToLive and invalidationInterval parameters.
     */
    protected void setInternalThreads() {
        if (invalidationInterval != Long.MAX_VALUE && invalidationInterval > 0) {
            internalThreadService.execute(this::forcedInvalidation);
        }
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {
            internalThreadService.execute(this::periodicallyEvict);
        }
    }

    /**
     * Periodically evicts the cache, removing the expired or otherwise irrelevant values.
     * The results depend on the particular implementation of the evict() function.
     */
    protected void periodicallyEvict() {
        while (true) {
            sleepUninterrupted(timeToLive);
            evict();
        }
    }

    /**
     * Checks if the particular entry is expired.
     */
    protected boolean isExpired(Map.Entry<K, GenericCacheValue<V>> entry) {
        long creationOrAccessTime = countdownFromCreation ? entry.getValue().getCreatedOn() : entry.getValue().getLastAccessed();
        return (System.currentTimeMillis() - creationOrAccessTime) > this.timeToLive;         //System.currentTimeMillis() is very slow on Linux though very fast on Windows, but System.nanoTime() the opposite. TODO: Utreda
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
