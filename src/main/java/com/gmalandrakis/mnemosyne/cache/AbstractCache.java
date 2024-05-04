package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.AbstractCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

/**
 * A general description of the Caches used by mnemosyne.
 * <p>
 * The fields declared here are explained in {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached}.
 * <p>
 * Implementations are expected, though not required, to have non-zero values in the aforementioned fields.
 * <p>
 * For custom implementations, it is strongly recommended that the Map containing the keys and the values
 * wraps the latter with an implementation of {@link AbstractCacheValue AbstractCacheValue},
 * i.e. that a Map&lt;K, ? extends AbstractCacheValue&lt;V&gt;&gt; is in use.
 *
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 * @see com.gmalandrakis.mnemosyne.structures.CacheParameters
 * @see AbstractGenericCache
 */
public abstract class AbstractCache<K, V> {
    String name;
    boolean countdownFromCreation = false;
    long expirationTime;
    long invalidationInterval;

    int capacity;

    short capacityPercentageForEviction;

    public AbstractCache(CacheParameters parameters) {
        this.capacity = (parameters.getCapacity() <= 0 ? Integer.MAX_VALUE : parameters.getCapacity());
        this.expirationTime = (parameters.getTimeToLive() <= 0 ? Long.MAX_VALUE : parameters.getTimeToLive());
        this.invalidationInterval = (parameters.getInvalidationInterval() < 0 ? Long.MAX_VALUE : parameters.getInvalidationInterval());
        this.name = parameters.getCacheName();
        this.countdownFromCreation = parameters.isCountdownFromCreation();
        this.capacityPercentageForEviction = (parameters.getPreemptiveEvictionPercentage() < 0 || parameters.getPreemptiveEvictionPercentage() > 100 ? 0 : parameters.getPreemptiveEvictionPercentage());
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
     */
    abstract void evict();

    /**
     * Invalidates cache completely.
     */
    abstract void invalidateCache();


}
