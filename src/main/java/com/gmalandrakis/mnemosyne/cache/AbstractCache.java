package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;

/**
 * A general description of the Caches used by mnemosyne.
 * <p>
 * Implementations are expected, though not required, to have non-zero values in the fields declared here.
 * Unless otherwise implemented, a negative or zero value in the fields translates to Long.MAX_VALUE.
 * <p>
 * For custom implementations, it is strongly recommended that the Map containing the keys and the values
 * wraps the latter with an implementation of {@link com.gmalandrakis.mnemosyne.core.AbstractCacheValue AbstractCacheValue},
 * i.e. that a Map&lt;K, ? extends AbstractCacheValue&lt;V&gt;&gt; is in use.
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 * @see com.gmalandrakis.mnemosyne.structures.CacheParameters
 * @see com.gmalandrakis.mnemosyne.cache.GenericCache
 */
public abstract class AbstractCache<K, V> {

    final long capacity;

    final long expirationTime;

    final long forcedEvictionInterval;

    /**
     * Determines whether the values expire expirationTime milliseconds after the last access (default)
     * or after creation.
     */
    boolean countdownFromCreation = false;

    final String name;

    public AbstractCache(CacheParameters parameters) {
        this.capacity = (parameters.getCapacity() <= 0 ? Long.MAX_VALUE : parameters.getCapacity());
        this.expirationTime = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.forcedEvictionInterval = (parameters.getForcedEvictionInterval() <= 0 ? Long.MAX_VALUE : parameters.getForcedEvictionInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
    }

    public abstract void put(K key, V value);

    public abstract V get(K key);

    /**
     * @return the name of the eviction algorithm
     */
    abstract String getAlgorithmName();

    /**
     * Used to retrieve the next element that is of most interest to the algorithm.
     * A use case example is getting the oldest element of a FIFO Cache.
     * *
     *
     * @return The key of the element that the eviction algorithm may target next.
     */
    abstract K getTargetKey();

    /**
     * Removes all the expired or otherwise irrelevant entries.
     * <p>
     * If the cache is an extension of the {@link com.gmalandrakis.mnemosyne.cache.GenericCache}, it will be
     * called periodically by a dedicated thread. The frequency depends on the given capacity and current size
     * (i.e. the fuller the cache, the more frequent the calls).
     * <p>
     */
    abstract void evict();

    /**
     * Invalidates cache completely.
     */
    abstract void evictAll();

    public long getCapacity() {
        return capacity;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public long getForcedEvictionInterval() {
        return forcedEvictionInterval;
    }

    public boolean isCountdownFromCreation() {
        return countdownFromCreation;
    }

    public String getName() {
        return name;
    }
}
