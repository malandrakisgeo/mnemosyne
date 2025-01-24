package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.IdWrapper;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A general description of the Caches used by mnemosyne.
 * <p>
 *
 * @param <K>  The type of the keys used to retrieve the cache elements.
 * @param <ID> The type of ID of the values stored in the ValuePool,
 * @param <V>  The type of the values stored in the ValuePool.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 * @see com.gmalandrakis.mnemosyne.structures.CacheParameters
 * @see AbstractGenericCache
 */
public abstract class AbstractMnemosyneCache<K, ID, V> {
    /**
     * A map from the keys to the related ID values.
     * This should be a ConcurrentMap in order for mnemosyne to properly work in a multithreaded application.
     */
    ConcurrentMap<K, IdWrapper<ID>> keyIdMapper;

    /**
     *
     * @param key
     * @param ídValueMap
     */
    public abstract void putAll(K key, Map<ID,V> ídValueMap);

    /**
     * Adds the given key-value pair to the cache.
     *
     * @param key
     * @param value
     */
    public abstract void put(K key, ID id, V value);

    public abstract Collection<V> getAll(K key);

    public abstract Collection<V> getAll(Collection<K> key);

    /**
     * Retrieves a value for a given key.
     *
     * @param key
     * @return
     */
    public abstract V get(K key);

    /**
     * Removes the key-value pair for a given key, and possibly the value
     * from the ValuePool (if no other caches are using it).
     * If the underlying cache stores Collections of objects, all associated objects
     * may be removed from the ValuePool if eligible.
     *
     * @param key
     */
    public abstract void remove(K key);

    /**
     * For caches that return Collections of objects.
     * Removes exactly one ID and possibly its' associated value from the ValuePool for the collection
     * corresponding to the given key.
     * If no key is provided, the ID is removed from all collections for all available keys.
     *
     * @param key
     * @param id
     */
    public abstract void removeOneFromCollection(K key, ID id);


    /**
     * @return the name of the eviction algorithm
     */
    public abstract String getAlgorithmName();

    /**
     * Used to retrieve the next element that is of most interest to the algorithm.
     * A use case example is getting the key of the object that should be evicted next.
     *
     * @return The key of the element that the eviction algorithm may target next.
     */
    public abstract K getTargetKey();

    /**
     * Removes all the expired or otherwise irrelevant entries.
     */
    public abstract void evict();

    /**
     * Invalidates cache completely.
     */
    public abstract void invalidateCache();

}
