package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.cache.old.IdWrapperOld;
import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractGenericCacheOld<K, ID, V> extends AbstractMnemosyneCache<K, ID, V> {
    final ExecutorService internalThreadService;

    final ValuePool<ID, V> valuePool;

    final ConcurrentHashMap<K, IdWrapperOld<ID>> keyIdMap;

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


    public AbstractGenericCacheOld(CacheParameters parameters, ValuePool<ID, V> valuePool) {
        super();
        this.keyIdMap = new ConcurrentHashMap<>();
        this.valuePool = valuePool;
        this.totalCapacity = (parameters.getCapacity() <= 0 ? Integer.MAX_VALUE : parameters.getCapacity());
        this.timeToLive = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.invalidationInterval = (parameters.getInvalidationInterval() < 0 ? Long.MAX_VALUE : parameters.getInvalidationInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
        this.preemptiveEvictionPercentage = (parameters.getPreemptiveEvictionPercentage() <= 0 || parameters.getPreemptiveEvictionPercentage() >= 100 ? 100 : parameters.getPreemptiveEvictionPercentage());
        this.evictionStepPercentage = (parameters.getEvictionStepPercentage() < 0 || parameters.getEvictionStepPercentage() > 100) ? 0 : parameters.getEvictionStepPercentage();
        this.actualCapacity = (totalCapacity * (preemptiveEvictionPercentage / 100f));
        if (parameters.getThreadPoolSize() != 0 && parameters.getThreadPoolSize() > 5) {
            internalThreadService = Executors.newFixedThreadPool(parameters.getThreadPoolSize());
        } else {
            internalThreadService = Executors.newCachedThreadPool();
        }
        this.handleCollectionKeysSeparately = parameters.isHandleCollectionKeysSeparately();
        this.returnsCollection = parameters.isReturnsCollection();
        setInternalThreads();
    }

    public abstract void putAll(K key, Map<ID,V> ídValueMap);

    public abstract void put(K key, ID id, V value);

    public abstract V get(K key);

    public abstract Collection<V> getAll(K key);

    public abstract Collection<V> getAll(Collection<K> key);

    public abstract void remove(K key);

    public abstract void removeOneFromCollection(K key, ID id);

    public abstract void evict();

    public abstract void invalidateCache();

    public boolean ishandleCollectionKeysSeparately() {
        return handleCollectionKeysSeparately;
    }

    public boolean handlesCollections() {
        return this.returnsCollection;
    }

    abstract boolean idUsedAlready(ID id);

    /**
     * Forcibly invalidates the cache at regular intervals, depending on the presence or absence of an invalidationInterval parameter in the cache.
     */
    protected void forcedInvalidation() {
        while (true) {
            GeneralUtils.sleepUninterrupted(timeToLive);
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
    protected boolean isExpired(Map.Entry<K, IdWrapperOld<ID>> entry) {
        long creationOrAccessTime = countdownFromCreation ? entry.getValue().getCreatedOn() : entry.getValue().getLastAccessed();
        return (System.currentTimeMillis() - creationOrAccessTime) > this.timeToLive;         //System.currentTimeMillis() is very slow on Linux though very fast on Windows, but System.nanoTime() the opposite.
    }

    /**
     * Shared with MnemoProxy
     */
    public ExecutorService getInternalThreadService() {
        return internalThreadService;
    }

    /**
     * Puts the thread to sleep for a number of milliseconds, and suppresses the interrupts.
     * This is only meant to be used by internal threads whose sole purpose is to do something periodically.
     */
   /* private void sleepUninterrupted(long sleepTime) {
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
    }*/

}
