package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.core.ValuePool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LRUCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {
//WIP
    private ConcurrentLinkedQueue<K> recencyQueue;

    public LRUCache(CacheParameters cacheParameters, ValuePool poolService) {
        super(cacheParameters, poolService);
        recencyQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void put(K key, ID id, T value) {
        if (key == null || value == null) {
            return;
        }
        //   if (cachedValues.size() >= actualCapacity) {
        //        this.evict(); //Ideally, this statement is never reached, and evict() is called by the internal threads before the size exceeds the capacity. But if multiple threads write concurrently in the cache, they will likely prevent the internal threads from evicting it.
        //    }
        // cachedValues.put(key, new GenericCacheValue<>(value));
        recencyQueue.add(key);
    }

    @Override
    public void putAll(K key, Map<ID, T> ids) {

    }


    @Override
    public T get(K key) {
        T toBeReturned = null;

        /*var cacheVal = this.cachedValues.get(key);
        if (cacheVal != null) {
            toBeReturned = cacheVal.get(); //increase hits & update timestamp
            recencyQueue.remove(key);
            recencyQueue.add(key); //send to tail
        }*/
        return toBeReturned;
    }

    @Override
    public Collection<T> getAll(K key) {
        return List.of();
    }

    @Override
    public Collection<T> getAll(Collection<K> key) {
        return List.of();
    }

    @Override
    public void remove(K key) {
        //cachedValues.remove(key);
        recencyQueue.remove(key);
    }

    @Override
    public void removeOneFromCollection(K key, ID id) {

    }

    @Override
    public String getAlgorithmName() {
        return "LRU";
    }

    @Override
    public K getTargetKey() {
        return recencyQueue.peek();
    }

    @Override
    public void evict() {

     /*   if (cachedValues.size() >= this.actualCapacity) {
            final float eviction = Math.max((totalCapacity * evictionStepPercentage / 100f), 1); //An tuxon to evictionStepPercentage einai mhdeniko, na afairethei toulaxiston ena entry

            for (int i = 0; i < eviction; i++) {
                var lru = recencyQueue.poll(); //Gibt das erste Element zuruck und entfernt es aus der Queue
                if (lru != null) {
                    cachedValues.remove(lru);
                } else {
                    break; //Om queue:n är tom då finns det (nog) inget att ta bort. Dubbelkolla och kanske byta till return
                }
            }
        }

        var expiredValues = cachedValues.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).toList(); //Io sono una anatra
        expiredValues.forEach(this::remove); //qifsha ropt*/
    }

    @Override
    public void invalidateCache() {

    }

    @Override
    boolean idUsedAlready(ID id) {
        return false;
    }
}
