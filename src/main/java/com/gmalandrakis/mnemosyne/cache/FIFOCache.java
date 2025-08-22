package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRetrievalException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.IdWrapper;
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
        In collection caches, a key corresponds to multiple IDs. We need a way to know how many keys are using an ID without transversing through
        the whole map of keys and their ID-collections every time.
        In single-value caches, more often than not, there is a 1-1 correspondence between keys and IDs. But this is not *guaranteed*
        (fun exercise: come up with cases where different keys may point to the same object/ID) , and especially when we
        are dealing with cache updates: we may want to use a key with a brand new value and corresponding ID.
        By keeping track of how many keys are using an ID, we can accurately inform the ValuePool that it may be time to get rid of an ID and its' corresponding
        object.
     */
    final ConcurrentHashMap<ID, Integer> numberOfUsesById = new ConcurrentHashMap<ID, Integer>();

    private int idsInCache = 0;

    public FIFOCache(CacheParameters parameters, ValuePool poolService) {
        super(parameters, poolService);
    }

    @Override
    public void putAll(K key, Collection<ID> map) {
        if (key == null || map == null || !returnsCollection) {
            return;
        }

        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
            this.evict();
        }
        //We avoid iterative calls to put(), to avoid checking the keyIdMapper and concurrentFIFOQueue multiple times. One time suffices.
        var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
        possibleValue.addAllToCollectionOrUpdate(map);

        map.forEach(this::addOrUpdateIdAndValue);

        if (!concurrentFIFOQueue.contains(key)) {
            concurrentFIFOQueue.add(key);
        }
    }

    @Override
    public void put(K key, ID id) {
        if (key == null || id == null) {
            return;
        }
        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
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
                    valuePool.put(id, false); //just update the current value
                    return;
                }
                removeOrDecreaseIdUses(oldId);
            }
            keyIdMapper.put(key, new SingleIdWrapper<ID>(id)); //if we used putIfAbsent, we would prevent the key from being updated with a brand new ID/value
        }

        addOrUpdateIdAndValue(id);

        if (!concurrentFIFOQueue.contains(key)) {
            concurrentFIFOQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
    }

    @Override
    public void putInAllCollections(ID id) {
        if (!returnsCollection || handleCollectionKeysSeparately) {
            return;
        }
        var initialNumOfUses = numberOfUsesById.get(id);
        int i = initialNumOfUses;
        for (K k : keyIdMapper.keySet()) {
            var idWrapper = ((CollectionIdWrapper) keyIdMapper.get(k));
            if (idWrapper.addToCollectionOrUpdate(id)) {
                numberOfUsesById.put(id, ++i);
            }
        }
        valuePool.put(id, initialNumOfUses == 0);
    }

    @Override
    public T get(K key) {
        if (!concurrentFIFOQueue.contains(key)) {
            return null;
        }
        var cachedIdData = keyIdMapper.get(key);
        if (cachedIdData == null) {
            concurrentFIFOQueue.remove(key);
            throw new MnemosyneRetrievalException("Key is present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
        }
        //TODO: Perhaps a cacheIdData with single Id could be used when handleCollectionKeysSeparately.
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
            concurrentFIFOQueue.remove(key);
            throw new MnemosyneRetrievalException("Key was present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
        }
        return valuePool.getAll(id.getIds());
    }

    @Override
    public Collection<T> getAll(Collection<K> key) {
        var all = new ArrayList<T>();
        for (K k : key) {
            if (returnsCollection) {
                all.addAll(this.getAll(k)); //TODO: UnitTest
            } else {
                all.add(this.get(k));
            }
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
        if (key == null) {
            removeById(List.of(id));
        } else {
            var cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
            if (cacheData == null) {
                return;
            }
            if (cacheData.getIds().remove(id)) {
                removeOrDecreaseIdUses(id);
            }
        }
    }

    @Override
    public void removeById(Collection<ID> ids) {
        var relatedKeys = new HashSet<K>();

        for (ID id : ids) {
            if (!returnsCollection) {
                for (K k : keyIdMapper.keySet()) {
                    if (((SingleIdWrapper) k).getId().equals(id)) {
                        relatedKeys.add(k);
                        removeOrDecreaseIdUses(id);
                    }
                }
            } else {
                for (K k : keyIdMapper.keySet()) {
                    var deleted = ((CollectionIdWrapper) k).getIds().remove(id);
                    if (deleted) {
                        relatedKeys.add(k);
                        removeOrDecreaseIdUses(id);
                    }
                }
            }
        }

        if (handleCollectionKeysSeparately || !returnsCollection) { //on special collection handling, a key corresponds to at most one ID
            relatedKeys.forEach(k -> {
                keyIdMapper.remove(k);
                concurrentFIFOQueue.remove(k);
            });
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
            var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey); //Filtrarisma prwtistws se aparxaiwmenes times, pou tha einai kai lathos an epistrafoun. TODO: Isws kana expiration check ston getter()?
            expiredValues.forEach(this::remove);
        }

        while (numberOfUsesById.size() >= this.actualCapacity) {
            var oldestElement = concurrentFIFOQueue.poll(); //Gibt das erste Element zuruck und entfernt es aus der Queue
            if (oldestElement != null) {
                remove(oldestElement);
            } else { //Om queue:n är tom då finns det (nog) inget att ta bort.
                break; //Io sono una anatra
            }
        }
        //qifsha ropt
    }

    @Override
    public void invalidateCache() {
        while (!concurrentFIFOQueue.isEmpty()) {
            var k = concurrentFIFOQueue.poll();
            remove(k);
        }
        /*
            When we preemptively added values in collection-caches without filtering out the ones already fetched,
            we ended up with a memory leak: the IDs were removed from concurrentFIFOQueue but were still referenced to
            by numberOfUsesById. We keep it as it is to help us find other mistakes in a while.
            TODO: Replace it manually after everything is tested thoroughly.
         */
        //numberOfUsesById = new ConcurrentHashMap<ID, Integer>();
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

    private void addOrUpdateIdAndValue(ID id) {
        var usesOfIdInCache = numberOfUsesById.getOrDefault(id, 0); //In non-collection caches, a key corresponds to just one object, but one object may be referenced to by many keys.
        var idAlreadyInCache = usesOfIdInCache > 0;
        numberOfUsesById.put(id, ++usesOfIdInCache);
        valuePool.put(id, !idAlreadyInCache);
    }

}
