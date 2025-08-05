package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.IdWrapper;
import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractGenericCache<K, ID, V> extends AbstractMnemosyneCache<K, ID, V> {
    final ExecutorService internalThreadService;
    final ValuePool<ID, V> valuePool;
    final String name;
    final boolean countdownFromCreation;
    final long timeToLive;
    final long invalidationInterval;
    final int totalCapacity;
    final float actualCapacity;
    final short preemptiveEvictionPercentage;
    final short evictionStepPercentage;
    final boolean handleCollectionKeysSeparately;
    final boolean returnsCollection;

    static final int MAX_MAP_SIZE = 1 << 30  - 1;

    public AbstractGenericCache(CacheParameters parameters, ValuePool<ID, V> valuePool) {
        super(parameters, valuePool, new ConcurrentHashMap<K, IdWrapper<ID>>());
        this.valuePool = valuePool;
        this.totalCapacity = (parameters.getCapacity() <= 0 ? MAX_MAP_SIZE : parameters.getCapacity()); //Hashmap's default total capacity
        this.timeToLive = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.invalidationInterval = (parameters.getInvalidationInterval() < 0 ? Long.MAX_VALUE : parameters.getInvalidationInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
        this.preemptiveEvictionPercentage = (parameters.getPreemptiveEvictionPercentage() <= 0 || parameters.getPreemptiveEvictionPercentage() >= 100 ? 100 : parameters.getPreemptiveEvictionPercentage());
        this.evictionStepPercentage = (parameters.getEvictionStepPercentage() < 0 || parameters.getEvictionStepPercentage() > 100) ? 0 : parameters.getEvictionStepPercentage();
        this.actualCapacity = (totalCapacity * (preemptiveEvictionPercentage / 100f));
        if (parameters.getThreadPoolSize() > 5) {
            internalThreadService = Executors.newFixedThreadPool(parameters.getThreadPoolSize());
        } else {
            internalThreadService = Executors.newCachedThreadPool();
        }
        this.handleCollectionKeysSeparately = parameters.isHandleCollectionKeysSeparately();
        this.returnsCollection = parameters.isReturnsCollection();
        setInternalThreads();
    }



    public boolean ishandleCollectionKeysSeparately() {
        return handleCollectionKeysSeparately;
    }

    public boolean handlesCollections() {
        return this.returnsCollection;
    }

    public abstract boolean idUsedAlready(ID id);

    /**
     * Forcibly invalidates the cache at regular intervals, depending on the presence or absence of an invalidationInterval parameter in the cache.
     */
    protected void forcedInvalidation() {
        while (true) {
            GeneralUtils.sleepUninterrupted(invalidationInterval);
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
            GeneralUtils.sleepUninterrupted(timeToLive);
            evict();
        }
    }

    /**
     * Checks if the particular entry is expired.
     */
    protected boolean isExpired(Map.Entry<K, IdWrapper<ID>> entry) {
        long creationOrAccessTime = countdownFromCreation ? entry.getValue().getCreatedOn() : entry.getValue().getLastAccessed();
        return (System.currentTimeMillis() - creationOrAccessTime) > this.timeToLive;    //System.currentTimeMillis() is very slow on Linux though very fast on Windows, but System.nanoTime() the opposite.
    }

    /**
     * Shared with MnemoProxy
     */
    public ExecutorService getInternalThreadService() {
        return internalThreadService;
    }

}
