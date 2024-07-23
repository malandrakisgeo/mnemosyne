package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.GenericCacheValue;

import java.util.*;

/**
 * Implementation of an LFU (Least Frequently Used) cache.
 *
 * @param <K> The type of the keys used to retrieve the cache elements.
 * @param <V> The type of the values stored in the cache.
 */
public class LFUCache<K, V> extends AbstractGenericCache<K, V> {

    protected final List<K> evictNext;

    public LFUCache(CacheParameters parameters) {
        super(parameters);
        evictNext = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }

        if (cachedValues.size() >= actualCapacity) {
            this.evict();
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

        if (cachedValues.size() >= actualCapacity) {
            evictNext.forEach(cachedValues::remove);
            evictNext.clear();
        }

    }

    /*
        We want a list of values that are to be evicted next.
        A naive way (and the very first implementation of LFU here) would be to have a HashMap of the numbers of hits, along with a Set of the keys that have
        this hit number, and remove all the keys of the least number in the keyset of the HashMap.

        Yet not only this suffers from the obvious problem that it removes most of the values on each eviction (in most applications, cache values are typically only accessed one or two times before evicted),
        but also not only demands thread-safe additions and removals to the HashMap every time we even just access an object of the cache.
        The resulting cache is, as a Greek-speaker would put it, "slower than death".

        This is a computationally more efficient solution, that generates a list of keys that can be evicted, based on the hits and their creation/lastAccess time.
        Whenever the cache becomes X% full, a list of the values that can soon be evicted is generated based on hits, as well as creation timestamp.

     */
    protected void setEvictNext() {
        synchronized (evictNext) {
            if (evictNext.isEmpty()) {
                var tempLimit = Math.max((int) (actualCapacity * evictionStepPercentage / 100f), 1);
                if (this.cachedValues.size() >= this.actualCapacity) {
                    tempLimit += (int) (this.cachedValues.size() - this.actualCapacity);
                }

                List<K> toBeEvicted = new ArrayList<>(cachedValues.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).toList());

                var tempLst = cachedValues.entrySet().stream()
                        .sorted(Comparator.comparingLong(v -> v.getValue().getCreatedOn()))
                        .sorted(Comparator.comparingInt(v -> v.getValue().getHits()))
                        .map(Map.Entry::getKey)
                        .limit(tempLimit)
                        .toList();
                toBeEvicted.addAll(tempLst);

                evictNext.addAll(Collections.synchronizedList(toBeEvicted));
            }
        }
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
        evictNext.clear();
    }


}