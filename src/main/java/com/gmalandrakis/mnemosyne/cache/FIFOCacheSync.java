package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
/**
    Slightly better performance than FIFOCache in single-threaded environments.
    Not recommended for multithreaded applications.
 */
public class FIFOCacheSync<K, V> extends AbstractCache<K, V> {

    private final ConcurrentMap<K, GenericCacheValue<V>> cachedValues = new ConcurrentHashMap<>();

    private final LinkedList<K> FIFOQueue = new LinkedList<>();

    public FIFOCacheSync(CacheParameters parameters) {
        super(parameters);
    }

    @Override
    public String getAlgorithmName() {
        return "FIFO";
    }

    @Override
    public K getTargetKey() {
        synchronized (FIFOQueue) {
            return FIFOQueue.getLast();
        }
    }

    @Override
    void evict() {
        return;
    }

    @Override
    public void put(K key, V value) {
        synchronized (FIFOQueue) {
            while (FIFOQueue.size() >= this.capacity) { //
                cachedValues.remove(FIFOQueue.getFirst());
                FIFOQueue.removeFirst();
            }
            FIFOQueue.add(key);
        }
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
    public void invalidateCache() {
        synchronized (FIFOQueue) {
            FIFOQueue.clear();
        }
        cachedValues.clear();
    }

}
