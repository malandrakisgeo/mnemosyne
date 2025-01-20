package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRetrievalException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FIFOCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {

    ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();

    Set<ID> idsInUse = returnsCollection ? Collections.synchronizedSet(new HashSet<>()) : null; //Meaningless to use for values with a 1-1 correspondence between keys and IDs.

    public FIFOCache(CacheParameters parameters, ValuePool poolService) {
        super(parameters, poolService);
    }


    @Override
    public void putAll(K key, Map<ID, T> map) {
        if (key == null || map == null || !returnsCollection) {
            return;
        }

        if (concurrentFIFOQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        var possibleValue = keyIdMapper.get(key);
        if (possibleValue != null) {
            var valueAsCollectionIdWrapper = (CollectionIdWrapper) possibleValue;
            valueAsCollectionIdWrapper.addAllToCollectionOrUpdate(map.keySet());
        } else {
            keyIdMapper.put(key, new CollectionIdWrapper<>(map.keySet()));
        }
        if (!concurrentFIFOQueue.contains(key)) {
            concurrentFIFOQueue.add(key);
        }

       /* for (int i = 0; i < idList.size(); i++) {
            valuePool.put(idList.get(i), valueList.get(i), !idUsedAlready(idList.get(i)));
        }*/
        map.forEach((id, val) -> {
            valuePool.put(id, val, !idUsedAlready(id));
            idsInUse.add(id);
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
        var idAlreadyInThisCache = false;
        var keyAlreadyInThisCache = concurrentFIFOQueue.contains(key);
        var existentIdWrapperForKey = keyIdMapper.get(key);

        if (returnsCollection) {
            idAlreadyInThisCache = idsInUse.contains(id);
            if (!idAlreadyInThisCache) {
                idsInUse.add(id);
            }

            if (existentIdWrapperForKey != null) {
                ((CollectionIdWrapper) existentIdWrapperForKey).addToCollectionOrUpdate(id);
            } else {
                keyIdMapper.put(key, new CollectionIdWrapper<>(Collections.singleton(id)));
            }

        } else {
            idAlreadyInThisCache = keyAlreadyInThisCache; //We have a one-key-to-one-id correlation. Checking for the key in the FIFO struct suffices.

            if (existentIdWrapperForKey != null) {
                if (((SingleIdWrapper) existentIdWrapperForKey).getId() != id) {
                    throw new MnemosyneRetrievalException("ID uniqueness violation or mistake during ID fetching");
                }
            } else {
                keyIdMapper.put(key, new SingleIdWrapper<>(id));
            }
        }
        if (!keyAlreadyInThisCache) {
            concurrentFIFOQueue.add(key);
        }
        valuePool.put(id, value, !idAlreadyInThisCache);

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

        internalThreadService.execute(() -> {

            if (returnsCollection) {
                var ids = ((CollectionIdWrapper) cacheData).getIds();
                Map<ID, Integer> map = valuePool.removeOrDecreaseNumberOfUsesForIds(ids);
                map.forEach((id, numOfUses) -> {
                    if (numOfUses == 0) {
                        idsInUse.remove(id);
                    }
                });
            } else {
                var id = (ID) ((SingleIdWrapper) cacheData).getId();
                valuePool.removeOrDecreaseNumberOfUsesForId(id);

            }
        });
    }

    @Override
    public void removeOneFromCollection(K key, ID id) {
        if (returnsCollection) {
            if (key == null) {
                for (K k : keyIdMapper.keySet()) {
                    ((CollectionIdWrapper) keyIdMapper.get(k)).getIds().remove(id);
                }

            } else {
                var cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
                if (cacheData == null) {
                    return;
                }
                cacheData.getIds().remove(id);
            }

            var numOfUses = valuePool.removeOrDecreaseNumberOfUsesForId(id);
            if (numOfUses == 0) {
                idsInUse.remove(id);
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
        while (concurrentFIFOQueue.size() >= this.actualCapacity) {
            var oldestElement = concurrentFIFOQueue.poll();
            if (oldestElement != null) {
                remove(oldestElement);
            }
        }
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {

            var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).collect(Collectors.toSet());
            expiredValues.forEach(this::remove);
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
        return idsInUse.contains(v);
    }

}
