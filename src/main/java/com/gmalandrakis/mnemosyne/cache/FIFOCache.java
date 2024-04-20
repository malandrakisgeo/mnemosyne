package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
    The default cache, a FIFO-policy implemented with a ConcurrentMap and a ConcurrentLinkedQueue.
    Strongly recommended for multithreaded environments.
 */
public class FIFOCache<K, V> extends GenericCache<K, V> {

    private final ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();


    public FIFOCache(CacheParameters parameters) {
        super(parameters);
        cachedValues =  new ConcurrentHashMap<>();
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
    void evict() {
        if (concurrentFIFOQueue.size() == this.capacity) { //Assumes that the size never exceeds the capacity. Is it so? Or are there concurrency problems that can lead to a size larger than the capacity? TODO; Check
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
        concurrentFIFOQueue.add(key);
        var cEntry = new GenericCacheValue<>(value);
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
    public void evictAll() {
        cachedValues.clear();
        concurrentFIFOQueue.clear();
    }
}
