package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of an LFU (Least Frequently Used) cache.
 * This implementation a "raw", strict LFU, meaning that nothing other than frequency is taken into account.
 * When the
 *
 * @param <K>
 * @param <V>
 */
public class LFUCache<K, V> extends GenericCache<K, V> {
    private final ConcurrentMap<K, GenericCacheValue<V>> cachedValues = new ConcurrentHashMap<>();

    private final ConcurrentMap<Long, ArrayList<K>> frequencyMap = new ConcurrentHashMap<>();

    public LFUCache(CacheParameters parameters) {
        super(parameters);
    }

    @Override
    public void put(K key, V value) {
        var inMemoryValue = cachedValues.get(key);
        if (inMemoryValue != null) {
            doOnUpdateOrRetrieval(key, inMemoryValue);
        }else{
            var map = frequencyMap.get(1L);
            if (map!=null){
                map.add(key);
            }else{
                ArrayList<K> lst = new ArrayList<>();
                lst.add(key);
                frequencyMap.put(1L,lst);
            }
        }
        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    @Override
    public V get(K key) {
        V toBeReturned = null;

        var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            toBeReturned = cacheVal.get(); //increase hits & update timestamp
            doOnUpdateOrRetrieval(key, cacheVal);
        }
        return toBeReturned;
    }

    @Override
    public String getAlgorithmName() {
        return "LFU";
    }

    @Override
    public K getTargetKey() {
        var minVals = Collections.min(frequencyMap.keySet());
        var v = frequencyMap.get(minVals).get(0); //TODO: Assumes not null.

        //TODO: Find the oldest element and return it
        return v;
    }


    /**
     * Removes expired values
     */
    @Override
    public void evict() {
        var minVals = Collections.min(frequencyMap.keySet());
        frequencyMap.remove(minVals);
    }

    @Override
    public void evictAll() {
        for (Long l : frequencyMap.keySet()) {
            frequencyMap.remove(l);
        }
    }

    private void doOnUpdateOrRetrieval(K key, GenericCacheValue<V> cacheVal) {
        var hits = cacheVal.getHits();
        this.frequencyMap.get(hits - 1).remove(key); //We assume it is never null because it has already been created either here or in addEntry
        var next = this.frequencyMap.get(hits);
        if (next == null) {
            ArrayList<K> lst = new ArrayList<>();
            lst.add(key);
            this.frequencyMap.put(hits, lst);
        } else {
            next.add(key);
        }
    }

}
