package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The default cache, a FIFO-policy implemented with a ConcurrentMap and a ConcurrentLinkedQueue.
 *
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class FIFOCache<K, V> extends AbstractGenericCache<K, V> {

    private final ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();


    public FIFOCache(CacheParameters parameters) {
        super(parameters);
        cachedValues = new ConcurrentHashMap<>();
        if (parameters.getTimeToLive() != Long.MAX_VALUE) {
            this.invalidationInterval = parameters.getTimeToLive();
            internalThreadService.submit(this::forcedInvalidation).isDone();
        }
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
        while (concurrentFIFOQueue.size() >= this.capacity) {
            cachedValues.remove(concurrentFIFOQueue.peek());
            concurrentFIFOQueue.remove();
        }
    }

    @Override
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        evict(); //Will evict if necessary
        var cEntry = new GenericCacheValue<>(value);
        concurrentFIFOQueue.add(key); //We don't check if the value already exists, since MnemoProxy already does this.
        cachedValues.put(key, cEntry);
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
