package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.IdWrapper;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * A general description of the Caches used by mnemosyne.
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
     * A map from the keys to the related ID values along with key-specific metadata.
     * This should be a ConcurrentMap or externally synchronized Map in order for mnemosyne to properly work in a multithreaded application.
     * <p>
     * The IdWrapper can be either implementation, depending on whether the cache is expected to return whole collections for
     * a key or single values.
     */
    Map<K, IdWrapper<ID>> keyIdMapper;

    /**
     * The ValuePool containing the mapping between IDs and values.
     * This is automatically instantiated by mnemosyne for every cached type as a singleton.
     * <p>
     * Every addition, update, or removal to a cache <b>must</b> update the ValuePool in order for mnemosyne to work properly with multiple caches and annotation-driven updates.
     */
    ValuePool<ID, V> valuePool;

    /**
     * The implementations are expected to have a constructor that takes a CacheParameter, a ValuePool, and a ConcurrentMap as arguments.
     * The CacheParameter and the ValuePool are instantiated by mnemosyne, whereas the ConcurrentMap is instantiated by the implementation itself.
     * Which CacheParameters will eventually be used is also up to the implementation.
     */
    public AbstractMnemosyneCache(CacheParameters parameters, ValuePool<ID, V> valuePool, ConcurrentMap<K, IdWrapper<ID>> concurrentMap) {
        this.keyIdMapper = concurrentMap;
        this.valuePool = valuePool;
    }


    /**
     * Adds the given key-value pair to the cache.
     * <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#put ValuePool's put()}
     */
    public abstract void put(K key, ID id, V value);

    /**
     * Adds multiple values along with their IDs for a single key.
     * Should only be implemented for collection caches.     <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#put ValuePool's put()}
     */
    public abstract void putAll(K key, Map<ID, V> ídValueMap);

    /**
     * Adds a particular ID in all available collection caches independently of the keys corresponding to them.
     * Should only be implemented for collection caches.
     * <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#put ValuePool's put()}
     **/
    public abstract void putInAllCollections(ID id, V value);

    /**
     * For collection caches -i.e. caches where one key can correspond to multiple values.
     * An empty collection should be returned if no value corresponds to the key.
     */
    public abstract Collection<V> getAll(K key);

    /**
     * Returns one or more values depending on the key collection. No 1-1 correlation between keys and values is assumed by mnemosyne
     * except for caches explicitly configured for special collection-handling.
     * <p>
     * Implementations should ignore keys that do not correspond to some value.
     * <p>
     * An empty collection should be returned if no key corresponds to a value.
     */
    public abstract Collection<V> getAll(Collection<K> key);

    /**
     * Retrieves a value for a given key.
     */
    public abstract V get(K key);

    /**
     * Removes the key-value pair for a given key, and possibly the value
     * from the ValuePool (if no other caches are using it).
     * If the underlying cache stores Collections of objects, all associated objects
     * may be removed from the ValuePool if eligible.
     * <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#removeOrDecreaseNumberOfUsesForId ValuePool's removeOrDecreaseNumberOfUsesForId()},
     * otherwise the values may not be evicted from the value pool.
     */
    public abstract void remove(K key);

    /**
     * For caches that return Collections of objects.
     * Removes exactly one ID and possibly its' associated value from the ValuePool for the collection
     * corresponding to the given key.
     * If no key is provided, the ID is removed from all collections for all available keys.
     * <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#removeOrDecreaseNumberOfUsesForId ValuePool's removeOrDecreaseNumberOfUsesForId()},
     * otherwise the values may not be evicted from the value pool.
     */
    public abstract void removeOneFromCollection(K key, ID id);


    /**
     * Removes an ID from a cache.
     * <p>
     * Implementations <b>must</b> call {@link com.gmalandrakis.mnemosyne.core.ValuePool#removeOrDecreaseNumberOfUsesForId ValuePool's removeOrDecreaseNumberOfUsesForId()},
     * otherwise the values may not be evicted from the value pool.
     **/

    public abstract void removeById(ID id);

    /**
     * @return the name of the eviction algorithm
     */
    public abstract String getAlgorithmName();

    /**
     * Used to retrieve the next element that is of most interest to the algorithm.
     * A use case example is getting the key of the object that should be evicted next.
     */
    public abstract K getTargetKey();

    /**
     * Removes all the expired or otherwise irrelevant entries. Implementations must act on the ValuePool too.
     */
    public abstract void evict();

    /**
     * Invalidates cache completely.
     */
    public abstract void invalidateCache();

    /**
     * Returns whether the ID is already used by the cache.
     * Implementations can either transverse through the ValuePool, or prefer a more computationally efficient solution,
     * e.g. keeping ordered collections of the current IDs, or storing them in Maps that return the number of times each key is being used.
     */
    public abstract boolean idUsedAlready(ID id);

    public Map<K, IdWrapper<ID>> getKeyIdMapper() {
        return keyIdMapper;
    }

    public ValuePool<ID, V> getValuePool() {
        return valuePool;
    }
}
