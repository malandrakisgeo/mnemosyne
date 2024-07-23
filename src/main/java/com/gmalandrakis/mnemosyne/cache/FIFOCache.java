package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The default cache, a FIFO-policy implemented with a ConcurrentMap and a ConcurrentLinkedQueue.
 * Note that it is a plain old FIFO. No evictionStepPercentage is taken into account.
 *
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class FIFOCache<K, V> extends AbstractGenericCache<K, V> {

    private final ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();


    public FIFOCache(CacheParameters parameters) {
        super(parameters);
    }


    @Override
    public String getAlgorithmName() {
        return "FIFO";
    }

    @Override
    public K getTargetKey() {
        return concurrentFIFOQueue.peek();
    }

    @Override
    public void evict() {
        while (concurrentFIFOQueue.size() >= this.actualCapacity) {
            var oldestElement = concurrentFIFOQueue.poll();
            if (oldestElement != null) {
                cachedValues.remove(oldestElement);
            }
        }

        var expiredValues = cachedValues.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).toList();
        expiredValues.forEach(this::remove);
    }

    @Override
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        var cacheEntry = new GenericCacheValue<>(value);
        concurrentFIFOQueue.add(key);
        cachedValues.put(key, cacheEntry);
    }

    @Override
    public V get(K key) {
        var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            return cacheVal.get();
        }
        return null;
    }

    @Override
    public void remove(K key) {
        cachedValues.remove(key);
        concurrentFIFOQueue.remove(key);
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
        concurrentFIFOQueue.clear();
    }
}
