package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRetrievalException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({"unchecked", "rawtypes"})
//WIP
public class LRUCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {    //WIP
    //WIP
    final ConcurrentLinkedQueue<K> recencyQueue = new ConcurrentLinkedQueue<>();

    public LRUCache(CacheParameters cacheParameters, ValuePool poolService) {
        super(cacheParameters, poolService);
    }

    /*
        In collection caches, a key corresponds to multiple IDs. We need a way to know how many keys are using an ID without transversing through
        the whole map of keys and all their ID-collections every time.
        In single-value caches, more often than not, there is a 1-1 correspondence between keys and IDs. But this is still not *guaranteed*
        (fun exercise: come up with cases where different keys may point to the same object/ID) , and especially when we
        are dealing with cache updates: we may want to use a key with a brand new value and corresponding ID.
        By keeping track of how many keys are using an ID, we can accurately inform the ValuePool that it may be time to get rid of an ID and its' corresponding
        object.
     */
    final ConcurrentHashMap<ID, Integer> numberOfUsesById = new ConcurrentHashMap<ID, Integer>();


    @Override
    public void putAll(K key, Map<ID, T> map) {
        if (key == null || map == null || !returnsCollection) {
            return;
        }

        if (recencyQueue.size() >= this.actualCapacity) {
            this.evict();
        }
        //We avoid iterative calls to put(), to avoid checking the keyIdMapper and concurrentFIFOQueue multiple times. One time suffices.
        var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
        possibleValue.addAllToCollectionOrUpdate(map.keySet());

        map.forEach(this::addOrUpdateIdAndValue);

        if (!recencyQueue.contains(key)) {
            recencyQueue.add(key);
        }
    }

    @Override
    public void putInAllCollections(ID id, T value) {
        if (!returnsCollection || handleCollectionKeysSeparately) {
            return;
        }
        var initialNumOfUses = numberOfUsesById.get(id);
        int i = initialNumOfUses;
        for (K k : keyIdMapper.keySet()) {
            var c = ((CollectionIdWrapper) keyIdMapper.get(k));
            if (c.addToCollectionOrUpdate(id)) {
                numberOfUsesById.put(id, ++i);
            }
        }
        valuePool.put(id, value, initialNumOfUses == 0);
    }

    @Override
    public void put(K key, ID id, T value) {
        if (key == null || id == null) {
            return;
        }
        if (recencyQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        if (returnsCollection) {
            var idWrapper = (CollectionIdWrapper) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper());
            idWrapper.addToCollectionOrUpdate(id); //Unlike single-value caches, removing an old ID from a collection cache is not as simple as just replacing it a newer one. Only a manual call to removeOneFromCollection() or expiration can remove it.
        } else {
            var idWrapper = keyIdMapper.get(key);
            if (idWrapper != null) {
                var oldId = (ID) ((SingleIdWrapper) idWrapper).getId();
                if (oldId.equals(id)) {
                    valuePool.put(id, value, false); //just update the current value
                    return;
                }
                removeOrDecreaseIdUses(oldId);
            }
            keyIdMapper.put(key, new SingleIdWrapper<>(id)); //if we used putIfAbsent, we would prevent the key from being updated with a brand new ID/value
        }

        addOrUpdateIdAndValue(id, value);

        if (!recencyQueue.contains(key)) {
            recencyQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
    }

    @Override
    public T get(K key) {
        if (!recencyQueue.contains(key)) {
            return null;
        }
        var cachedIdData = keyIdMapper.get(key);
        if (cachedIdData == null) {
            recencyQueue.remove(key);
            throw new MnemosyneRetrievalException("Key is present in recency queue, buy not in keyIdMap: " + key.toString());
        }
        //TODO: If handleCollectionKeysSeparately, a cacheIdData with single Id should be used.
        ID id = (ID) (handleCollectionKeysSeparately ? ((CollectionIdWrapper) cachedIdData).getIds().toArray()[0] : ((SingleIdWrapper) cachedIdData).getId());
        recencyQueue.remove(key);
        recencyQueue.add(key);
        return valuePool.getValue(id);
    }

    @Override
    public Collection<T> getAll(K key) {
        if (!returnsCollection || !recencyQueue.contains(key)) {
            return Collections.emptyList();
        }
        var id = (CollectionIdWrapper) keyIdMapper.get(key);
        if (id == null) {
            recencyQueue.remove(key);
            throw new MnemosyneRetrievalException("Key is present in recency queue, buy not in keyIdMap: " + key.toString());
        }
        recencyQueue.remove(key);
        recencyQueue.add(key);

        return valuePool.getAll(id.getIds());
    }

    @Override
    public Collection<T> getAll(Collection<K> key) {
        var all = new ArrayList<T>();
        for (K k : key) {
            all.add(this.get(k));
        }
        return all;
    }

    @Override
    public void remove(K key) {
        var cacheData = keyIdMapper.get(key);
        if (cacheData == null) {
            return;
        }
        recencyQueue.remove(key);
        keyIdMapper.remove(key);

        if (returnsCollection) {
            Collection<ID> ids = ((CollectionIdWrapper) cacheData).getIds();
            ids.forEach(this::removeOrDecreaseIdUses);
        } else {
            var id = (ID) ((SingleIdWrapper) cacheData).getId();
            removeOrDecreaseIdUses(id);
        }
    }

    @Override
    public void removeOneFromCollection(K key, ID id) {
        if (!returnsCollection) {
            return;
        }
        if (key == null) { //deletes the ID from all collections!
            removeFromAllCollections(id);
        } else {
            var cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
            if (cacheData == null) {
                return;
            }
            cacheData.getIds().remove(id);
            removeOrDecreaseIdUses(id);
        }

    }

    @Override
    public void removeFromAllCollections(ID id) {
        if (!returnsCollection) {
            return;
        }
        var relatedKeys = new HashSet<K>();
        for (K k : keyIdMapper.keySet()) {
            var deleted = ((CollectionIdWrapper) keyIdMapper.get(k)).getIds().remove(id);
            if (deleted) {
                relatedKeys.add(k);
                removeOrDecreaseIdUses(id);
            }
        }
        if (handleCollectionKeysSeparately) { //on special collection handling, a key corresponds to at most one ID
            relatedKeys.forEach(k -> {
                keyIdMapper.remove(k);
                recencyQueue.remove(k);
            });
        }
    }

    @Override
    public String getAlgorithmName() {
        return "FIFO";
    }

    @Override
    public K getTargetKey() {
        return recencyQueue.poll();
    }

    @Override
    public void evict() {
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {
            var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey);
            expiredValues.forEach(this::remove);
        }

        while (numberOfUsesById.size() >= this.actualCapacity) {
            var oldestElement = recencyQueue.poll();
            if (oldestElement != null) {
                remove(oldestElement);
            }else{
                break;
            }
        }
    }

    @Override
    public void invalidateCache() {
        while (!recencyQueue.isEmpty()) {
            var k = recencyQueue.poll();
            remove(k);
        }
    }

    @Override
    public boolean idUsedAlready(ID v) {
        var numberOfCollectionsUsingIt = numberOfUsesById.get(v);
        return numberOfCollectionsUsingIt != null && numberOfCollectionsUsingIt > 0;
    }


    private void removeOrDecreaseIdUses(ID id) {
        var numOfCollectionsUsingId = numberOfUsesById.getOrDefault(id, 0) - 1;
        if (numOfCollectionsUsingId <= 0) {
            numberOfUsesById.remove(id);
            valuePool.removeOrDecreaseNumberOfUsesForId(id);
        } else {
            numberOfUsesById.put(id, numOfCollectionsUsingId);
        }
    }

    private void addOrUpdateIdAndValue(ID id, T value) {
        var usesOfIdInCache = numberOfUsesById.getOrDefault(id, 0); //In non-collection caches, a key corresponds to just one object, but one object may be referenced to by many keys.
        var idAlreadyInCache = usesOfIdInCache > 0;
        numberOfUsesById.put(id, ++usesOfIdInCache);
        valuePool.put(id, value, !idAlreadyInCache);
    }
}
