package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;
import com.gmalandrakis.mnemosyne.core.ValuePool;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FIFOCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {

    ConcurrentLinkedQueue<K> concurrentFIFOQueue = new ConcurrentLinkedQueue<>();
    Set<ID> idsInUse = returnsCollection ? Collections.synchronizedSet(new HashSet<>()) : null; //TODO: Map to integer (multiple uses) or delete.

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

        var possibleValue = keyIdMap.get(key);
        if (possibleValue != null) {
            var valueAsCollectionIdWrapper = (CollectionIdWrapper) possibleValue;
            valueAsCollectionIdWrapper.addAllToCollectionOrUpdate(map.keySet());
        } else {
            keyIdMap.put(key, new CollectionIdWrapper<>(map.keySet()));
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
        var existentIdWrapperForKe = keyIdMap.get(key);

        if (returnsCollection) {
            idAlreadyInThisCache = idsInUse.contains(id);
            if (!idAlreadyInThisCache) {
                idsInUse.add(id);
            }

            if (existentIdWrapperForKe != null) {
                ((CollectionIdWrapper) existentIdWrapperForKe).addToCollectionOrUpdate(id);
            } else {
                keyIdMap.put(key, new CollectionIdWrapper<>(Collections.singleton(id)));
            }

        } else {
            idAlreadyInThisCache = keyAlreadyInThisCache; //We have a one-key-to-one-id correlation. Checking for the key in the FIFO struct suffices.

            if (existentIdWrapperForKe != null) {
                if (((SingleIdWrapper) existentIdWrapperForKe).getId() != id) {
                    throw new RuntimeException("ID uniqueness violation or mistake during ID fetching");
                }
                existentIdWrapperForKe.updateLastAccessed();
            } else {
                keyIdMap.put(key, new SingleIdWrapper<>(id));
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
        var cachedIdData = keyIdMap.get(key);
        if (cachedIdData == null) {
            throw new RuntimeException("Key is present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
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
        var id = (CollectionIdWrapper) keyIdMap.get(key);
        if (id == null) {
            return Collections.emptyList();
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

        var cacheData = keyIdMap.get(key);
        if (cacheData == null) {
            return;
        }
        concurrentFIFOQueue.remove(key);

        keyIdMap.remove(key);

        internalThreadService.execute(() -> {

            if (returnsCollection) {
                var ids = ((CollectionIdWrapper) cacheData).getIds();
                valuePool.removeOrDecreaseNumberOfUsesForIds(ids);
                idsInUse.removeAll(ids);
            } else {
                var id = (ID) ((SingleIdWrapper) cacheData).getId();
                valuePool.removeOrDecreaseNumberOfUsesForId(id);
                idsInUse.remove(id);
            }

        });

    }

    @Override
    public void removeOneFromCollection(K key, ID id) {
        if (returnsCollection) {
            if (key == null) {
                for (K k : keyIdMap.keySet()) {
                    ((CollectionIdWrapper) keyIdMap.get(k)).getIds().remove(id);
                }

            } else {
                var cacheData = (CollectionIdWrapper) keyIdMap.get(key);
                if (cacheData == null) {
                    return;
                }
                cacheData.getIds().remove(id);
            }

            valuePool.removeOrDecreaseNumberOfUsesForId(id);
            idsInUse.remove(id);
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

            var expiredValues = keyIdMap.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).collect(Collectors.toSet());
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
        return this.idsInUse.contains(v);
    }

}
