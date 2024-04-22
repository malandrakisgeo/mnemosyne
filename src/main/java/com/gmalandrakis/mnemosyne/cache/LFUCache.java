package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.GenericCacheValue;
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
public class LFUCache<K, V> extends GenericCache<K, V> {

    private List<K> evictNext; //TODO: Make it concurrent

    public LFUCache(CacheParameters parameters) {
        super(parameters);
        cachedValues = new ConcurrentHashMap<>();
        //this.internalThreadService.submit(this::setEvictNext);
        evictNext = Collections.synchronizedList(Collections.emptyList());
    }

    @Override
    public void put(K key, V value) {
        if (evictNext.isEmpty() && cachedValues.size() >= capacity / 2) { //When cache is at least 50% full.
            this.setEvictNext();
        }
        if (cachedValues.size() >= capacity * 95/100) { //The cache should never be fuller than 95%, unless inevitable by JVM concurrency
            this.evict();
        }
        /*var inMemoryValue = cachedValues.get(key);
        if (inMemoryValue != null) {
            //  doOnUpdateOrRetrieval(key, inMemoryValue);
        } else {

        }*/
        cachedValues.put(key, new GenericCacheValue<>(value));
    }

    @Override
    public V get(K key) {
        V toBeReturned = null;

        var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            toBeReturned = cacheVal.get(); //increase hits & update timestamp
            //  doOnUpdateOrRetrieval(key, cacheVal);
        }
        return toBeReturned;
    }

    @Override
    public String getAlgorithmName() {
        return "LFU";
    }

    @Override
    public K getTargetKey() {
        if(!evictNext.isEmpty()){
            return evictNext.get(0);
        }
        return null; //A null is better than a random value
    }


    @Override
    public void evict() {
        synchronized (evictNext){
            if (evictNext.isEmpty()) {
                this.setEvictNext();
            }
            evictNext.forEach(k -> cachedValues.remove(k));
            evictNext = Collections.synchronizedList(Collections.emptyList());
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
        Whenever the cache becomes 50% full, a list of the values that can soon be evicted is generated based on hits, as well as creation timestamp.
        No expiration date is taken into account here, but remember you are free to implement your own algorithms.
     */
    private synchronized void setEvictNext() {
        if(this.evictNext.isEmpty()){
            this.evictNext = cachedValues.entrySet().stream()
                    .sorted(Comparator.comparing(v -> v.getValue().getHits()))
                    .sorted(Comparator.comparing(v -> v.getValue().getCreatedOn())) //TODO: or last accessed
                    .map(Map.Entry::getKey)
                    .limit(capacity / 10) //Removes up to 10% of the values. Otherwise it would be called more often, which would be computationally inefficient. More than 10% might result in unnecessary memory overhead.
                    .toList();
        }
    }

    @Override
    public void invalidateCache() {
        cachedValues.clear();
        evictNext = Collections.synchronizedList(Collections.emptyList());
    }


}