package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.AbstractCacheValue;

/**
 * A general description of the Caches used by mnemosyne.
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
public abstract class AbstractMnemosyneCache<K, V> {

    public AbstractMnemosyneCache() {
    }

    public abstract void put(K key, V value);

    public abstract V get(K key);

    /**
     * @return the name of the eviction algorithm
     */
    public abstract String getAlgorithmName();

    /**
     * Used to retrieve the next element that is of most interest to the algorithm.
     * A use case example is getting the oldest element of a FIFO Cache.
     * *
     *
     * @return The key of the element that the eviction algorithm may target next.
     */
    public abstract K getTargetKey();

    /**
     * Removes all the expired or otherwise irrelevant entries.
     * <p>
     */
    public abstract void evict();

    /**
     * Invalidates cache completely.
     */
    public abstract void invalidateCache();


}
