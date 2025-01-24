package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRetrievalException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The default implementation of a FIFO cache.
 * <p>
 * The cache uses an underlying ValuePool that maps the IDs of the values with the values themselves.
 * <p>
 * A mnemosyne cache can either return whole collections of objects, or return one object at the time.
 * Since multiple keys can be referring to the same IDs, the default FIFO cache uses a map that maps every
 * ID with the number of uses (i.e. the number of keys referring to it), as well as a ConcurrentLinkedQueue
 * that ensures the FIFO ordering on evictions.
 *
 * @param <K>
 * @param <ID>
 * @param <T>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FIFOCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {

    final ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();

    /*
        In collection-caches, a key corresponds to multiple IDs. We need a way to know how many keys are using an ID without transversing through
        the whole map of keys and their ID-collections every time.
        In single-key-caches, more often than not, there is a 1-1 correspondence between keys and IDs. But this is not *guaranteed*
        (fun exercise: come up with cases where the same Object/ID is the "answer" to multiple keys) , and especially when we
        are dealing with cache updates: we may want to use a key with a brand new value and corresponding ID.
        By keeping track of how many keys are using an ID, we can accurately inform the ValuePool that it may be time to get rid of an ID and its' corresponding
        object.
     */
    final ConcurrentHashMap<ID, Integer> numberOfUsesById = new ConcurrentHashMap<ID, Integer>();

    public FIFOCache(CacheParameters parameters, ValuePool poolService) {
        super(parameters, poolService);
    }

    @Override
    public void putAll(K key, Map<ID, T> map) {
        if (key == null || map == null) {
            return;
        }

        if (!returnsCollection) {
            map.forEach((id, val) -> {
                put(key, id, val);
            });
            return;
        }

        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
        possibleValue.addAllToCollectionOrUpdate(map.keySet());

        if (!concurrentFIFOQueue.contains(key)) {
            concurrentFIFOQueue.add(key);
        }

        map.forEach((id, val) -> {
            var numberOfCollectionsUsingId = numberOfUsesById.getOrDefault(id, 0);
            var idUsedAlready = numberOfCollectionsUsingId != 0;
            numberOfUsesById.put(id, ++numberOfCollectionsUsingId);
            valuePool.put(id, val, !idUsedAlready);
        });
    }

    @Override
    public void put(K key, ID id, T value) {
        if (key == null || id == null) {
            return;
        }
        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        if (returnsCollection) {
            putInCollection(key, id, value);
        } else {
            putInSingle(key, id, value);
        }
    }

    @Override
    public T get(K key) {
        if (!concurrentFIFOQueue.contains(key)) {
            return null;
        }
        var cachedIdData = keyIdMapper.get(key);
        if (cachedIdData == null) {
            throw new MnemosyneRetrievalException("Key is present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
        }
        //TODO: If handleCollectionKeysSeparately, a cacheIdData with single Id should be used.
        ID id = (ID) (handleCollectionKeysSeparately ? ((CollectionIdWrapper) cachedIdData).getIds().toArray()[0] : ((SingleIdWrapper) cachedIdData).getId());

        return valuePool.getValue(id);
    }

    @Override
    public Collection<T> getAll(K key) {
        if (!returnsCollection || !concurrentFIFOQueue.contains(key)) {
            return Collections.emptyList();
        }
        var id = (CollectionIdWrapper) keyIdMapper.get(key);
        if (id == null) {
            throw new MnemosyneRetrievalException("Key is present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
        }
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
        concurrentFIFOQueue.remove(key);
        keyIdMapper.remove(key);

        if (returnsCollection) {
            Collection<ID> ids = ((CollectionIdWrapper) cacheData).getIds();
            ids.forEach(this::removeOrDecrease);
        } else {
            var id = (ID) ((SingleIdWrapper) cacheData).getId();
            // valuePool.removeOrDecreaseNumberOfUsesForId(id);
            removeOrDecrease(id);
        }


    }

    @Override
    public void removeOneFromCollection(K key, ID id) {
        if (returnsCollection) {
            if (key == null) { //delete the ID from all collections!
                var relatedKeys = new HashSet<>();
                for (K k : keyIdMapper.keySet()) {
                    var deleted = ((CollectionIdWrapper) keyIdMapper.get(k)).getIds().remove(id);

                    if (deleted) {
                        relatedKeys.add(k);
                    }
                    numberOfUsesById.remove(id);
                    valuePool.removeOrDecreaseNumberOfUsesForId(id);
                }
                if (handleCollectionKeysSeparately) { //TODO: Perhaps you should treat the handleCollectionKeysSeparately as non-collection-cache for removing.
                    relatedKeys.forEach(k -> {
                        keyIdMapper.remove(k);
                        concurrentFIFOQueue.remove(k);
                    });
                }

            } else {
                var cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
                if (cacheData == null) {
                    return;
                }
                cacheData.getIds().remove(id); //o prwtos pou tha mou steilei email gia auto to sxolio lamvanei pente evrw.
                removeOrDecrease(id);
            }

            var numOfUses = valuePool.removeOrDecreaseNumberOfUsesForId(id);
            if (numOfUses == 0) {
                numberOfUsesById.remove(id);
            }
        }
    }

    @Override
    public String getAlgorithmName() {
        return "FIFO";
    }

    @Override
    public K getTargetKey() {
        return concurrentFIFOQueue.poll();
    }

    @Override
    public void evict() {
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {
            var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey);
            expiredValues.forEach(this::remove);
        }

        while (concurrentFIFOQueue.size() >= this.actualCapacity) {
            var oldestElement = concurrentFIFOQueue.poll();
            if (oldestElement != null) {
                remove(oldestElement);
            }
        }
    }

    @Override
    public void invalidateCache() {
        while (!concurrentFIFOQueue.isEmpty()) {
            var k = concurrentFIFOQueue.poll();
            remove(k);
        }
    }

    @Override
    boolean idUsedAlready(ID v) { //TODO: Delete this disgrace when coming up with something better
        var numberOfCollectionsUsingIt = numberOfUsesById.get(v);
        return numberOfCollectionsUsingIt != null && numberOfCollectionsUsingIt > 0;
    }

    private void putInCollection(K key, ID id, T value) {
        var keyAlreadyInThisCache = concurrentFIFOQueue.contains(key);
        var idWrapper = (CollectionIdWrapper) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper());

        var numberOfCollectionsUsingId = numberOfUsesById.getOrDefault(id, 0);
        var idUsedAlready = numberOfCollectionsUsingId != 0;
        numberOfUsesById.put(id, ++numberOfCollectionsUsingId);

        idWrapper.addToCollectionOrUpdate(id);

        if (!keyAlreadyInThisCache) {
            concurrentFIFOQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
        valuePool.put(id, value, !idUsedAlready);
    }

    private void putInSingle(K key, ID id, T value) {

        var keyAlreadyInThisCache = keyIdMapper.get(key);
        if (keyAlreadyInThisCache != null && ((SingleIdWrapper) keyAlreadyInThisCache).getId().equals(id)) { //BUG AFTER DELETE TODO
            return;
        }
        var usesOfIdInCache = numberOfUsesById.getOrDefault(id, 0); //In non-collection caches, a key corresponds to just one object, but one object may be referenced to by many keys.
        var idAlreadyInCache = usesOfIdInCache > 0;

        keyIdMapper.put(key, new SingleIdWrapper<>(id));
        if (keyAlreadyInThisCache == null) {
            concurrentFIFOQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
        numberOfUsesById.put(id, ++usesOfIdInCache);
        valuePool.put(id, value, !idAlreadyInCache);
    }

    private void removeOrDecrease(ID id) {
        var numOfCollectionsUsingId = numberOfUsesById.getOrDefault(id, 0) - 1; //TODO: as is, or -1?
        if (numOfCollectionsUsingId <= 0) {
            numberOfUsesById.remove(id);
            valuePool.removeOrDecreaseNumberOfUsesForId(id);
        } else {
            numberOfUsesById.put(id, numOfCollectionsUsingId);
        }
    }

    private void localMapAdd(ID id) {
        var numOfCollectionsUsingId = numberOfUsesById.getOrDefault(id, 0);
        numberOfUsesById.put(id, ++numOfCollectionsUsingId);

    }

}
