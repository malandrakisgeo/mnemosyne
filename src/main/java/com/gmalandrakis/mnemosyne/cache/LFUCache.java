package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of an LFU (Least Frequently Used) cache.
 * This implementation a "raw", strict LFU, meaning that nothing other than frequency is taken into account.
 * When the
 *
 * @param <K>
 * @param <V>
 */
public class LFUCache<K, V> extends AbstractGenericCache<K, V> {

    protected List<K> evictNext;
    private int limit;

    public LFUCache(CacheParameters parameters) {
        super(parameters);
        cachedValues = new ConcurrentHashMap<>();
        evictNext = Collections.synchronizedList(new ArrayList<>());
        limit = Math.max((int) (capacity * 0.15), 1);
    }

    @Override
    public void put(K key, V value) { //We don't check if the value already exists, since MnemoProxy already does this.
        if (cachedValues.size() >= capacity) {
            this.evict(); //Ideally, this statement is never reached, and evict() is called by the internal threads before the size exceeds the capacity. But if multiple threads write concurrently in the cache, they will likely prevent the internal threads from evicting it.
        }

        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    @Override
    public V get(K key) {
        V toBeReturned = null;

        var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            toBeReturned = cacheVal.get(); //increase hits & update timestamp
        }
        return toBeReturned;
    }

    @Override
    public String getAlgorithmName() {
        return "LFU";
    }

    @Override
    public K getTargetKey() {
        if (!evictNext.isEmpty()) {
            return evictNext.get(0);
        }
        return null; //A null is better than a random value
    }


    @Override
    public synchronized void evict() {
        if (evictNext.isEmpty()) {
            this.setEvictNext();
        }

        if (cachedValues.size() >= capacity * capacityPercentageForEviction / 100) {
            evictNext.forEach(cachedValues::remove);
            evictNext.clear();

        }

    }

    /*
        We want a list of values that are to be evicted next.
        A naive way (and the very first implementation of LFU here) would be to have a HashMap of the numbers of hits, along with a Set of the keys that have
        this hit number, and remove all the keys of the least number in the keyset of the HashMap.

        Yet not only this suffers from the obvious problem that it removes most of the values on each eviction (most cache values are only accessed one or two times before evicted),
        but also not only demands thread-safe additions and removals to the HashMap every time we even just access an object of the cache.
        The resulting cache is, as a Greek-speaker would put it, "slower than death".

        This is a computationally more efficient solution, that generates a list of keys that can be evicted, based on the hits and their creation/lastAccess time.
        Whenever the cache becomes X% full, a list of the values that can soon be evicted is generated based on hits, as well as creation timestamp.

     */

    protected void setEvictNext() {
        synchronized (evictNext) {
            if (evictNext.isEmpty()) {
                var tempLimit = limit;
                if (this.cachedValues.size() >= this.capacity) {
                    tempLimit = limit + this.cachedValues.size() - this.capacity;
                }

                List<K> toBeEvicted = new ArrayList<>();

                cachedValues.entrySet().forEach(kGenericCacheValueEntry -> {
                    if (isExpired(kGenericCacheValueEntry)) {
                        toBeEvicted.add(kGenericCacheValueEntry.getKey());
                    }
                });
                var tempLst = cachedValues.entrySet().stream()
                        .sorted(Comparator.comparingLong(v -> v.getValue().getCreatedOn()))
                        .sorted(Comparator.comparingInt(v -> v.getValue().getHits()))
                        .map(Map.Entry::getKey)
                        .limit(tempLimit) //Removes up to 15% of the values. Otherwise it would be called more often, which would be computationally inefficient. More than 15% might result in unnecessary memory overhead.
                        .toList();
                toBeEvicted.addAll(tempLst);

                evictNext = Collections.synchronizedList(toBeEvicted);
            }
        }
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
        evictNext = Collections.synchronizedList(new ArrayList<>());
    }

    private boolean isExpired(Map.Entry<K, GenericCacheValue<V>> entry) {
        long chosenVal = countdownFromCreation ? entry.getValue().getCreatedOn() : entry.getValue().getLastAccessed();
        return (System.currentTimeMillis() - chosenVal) >= expirationTime;
    }


}