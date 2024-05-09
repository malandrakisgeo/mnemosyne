package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;


import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementation of an LRU (Least Recently Used) cache.
 *
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class LRUCache<K, V> extends AbstractGenericCache<K, V> {
    private final ConcurrentLinkedQueue<K> queue;

    public LRUCache(CacheParameters cacheParameters) {
        super(cacheParameters);
        queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void put(K key, V value) { //We don't check if the value already exists, since MnemoProxy already does this.
        while (cachedValues.size() >= capacity) {
            this.evict(); //Ideally, this statement is never reached, and evict() is called by the internal threads before the size exceeds the capacity. But if multiple threads write concurrently in the cache, they will likely prevent the internal threads from evicting it.
        }

        queue.add(key);
        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    @Override
    public V get(K key) {
        V toBeReturned = null;

        var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            toBeReturned = cacheVal.get(); //increase hits & update timestamp
            queue.remove(key);
            queue.add(key); //send to tail
        }
        return toBeReturned;
    }

    @Override
    public void remove(K key) {
        cachedValues.remove(key);
        queue.remove(key);
    }

    @Override
    public String getAlgorithmName() {
        return "LRU";
    }

    @Override
    public K getTargetKey() {
        return queue.peek();

    }

    @Override
    public void evict() {
        int eviction = Math.max((int) (capacity * evictionStepPercentage / 100f), 1); //An tuxon to evictionStepPercentage einai mhdeniko, na afairethei toulaxiston ena entry

        for (int i = 0; i < eviction; i++) {
            var lru = queue.poll(); //Gibt das erste Element zuruck und entfernt es aus der Queue
            if (lru != null) {
                cachedValues.remove(lru);
            } else {
                return; //Om queue:n är tom då finns det inget att ta bort.
            }
        }

        var expired = cachedValues.entrySet().stream()
                .filter(this::isExpired)
                .map(Map.Entry::getKey)
                .toList();
        queue.remove(expired);  //Io sono una anatra
        expired.forEach(cachedValues::remove); //qifsha ropt
    }
}
