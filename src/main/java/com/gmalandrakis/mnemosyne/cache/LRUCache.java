package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.IdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "rawtypes"})
public class LRUCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {

    final ConcurrentHashMap<ID, Integer> numberOfUsesById = new ConcurrentHashMap<ID, Integer>();

    public LRUCache(CacheParameters cacheParameters, ValuePool poolService) {
        super(cacheParameters, poolService);
        this.keyIdMapper = new LinkedHashMap<K, IdWrapper<ID>>(totalCapacity, 0.75F, true);

    }

    @Override
    public void putAll(K key, Collection<ID> map) {
        if (key == null || map == null || !returnsCollection) {
            return;
        }
        if (keyIdMapper.size() >= this.actualCapacity) {
            this.evict();
        }
        //We avoid iterative calls to put(), to avoid checking the keyIdMapper multiple times. One time suffices.
        synchronized (keyIdMapper) {
            var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
            possibleValue.addAllToCollectionOrUpdate(map);
        }

        map.forEach(this::addOrUpdateIdAndValue);
    }

    @Override
    public void putInAllCollections(ID id) {
        if (!returnsCollection || handleCollectionKeysSeparately) {
            return;
        }
        var initialNumOfUses = numberOfUsesById.get(id);
        int i = initialNumOfUses;
        synchronized (keyIdMapper) {
            for (K k : keyIdMapper.keySet()) {
                var idWrapper = ((CollectionIdWrapper) keyIdMapper.get(k));
                if (idWrapper.addToCollectionOrUpdate(id)) {
                    numberOfUsesById.put(id, ++i);
                }
            }
        }
        valuePool.put(id, initialNumOfUses == 0);
    }

    @Override
    public void put(K key, ID id) {
        if (key == null || id == null) {
            return;
        }
        if (keyIdMapper.size() >= this.actualCapacity) {
            this.evict();
        }
        synchronized (keyIdMapper) {

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
        }
        addOrUpdateIdAndValue(id);

    }

    @Override
    public T get(K key) {
        if (!keyIdMapper.containsKey(key)) {
            return null;
        }
        synchronized (keyIdMapper) {
            var id = ((SingleIdWrapper) keyIdMapper.get(key)).getId();
            return valuePool.getValue((ID) id);
        }
    }

    @Override
    public Collection<T> getAll(K key) {
        if (!returnsCollection || !keyIdMapper.containsKey(key)) {
            return Collections.emptyList();
        }
        synchronized (keyIdMapper) {
            var ids = ((CollectionIdWrapper) keyIdMapper.get(key));
            if (ids != null) {
                return valuePool.getAll(ids.getIds());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<T> getAll(Collection<K> key) {
        var all = new HashSet<T>();
        synchronized (keyIdMapper) { //we avoid iterated calls to get() or getAll(K key) because we need to lock only once for the whole procedure.
            for (K k : key) {
                if (returnsCollection) {
                    var p = this.keyIdMapper.get(k);
                    if (p != null) {
                        var ids = ((CollectionIdWrapper) p).getIds();
                        all.addAll(valuePool.getAll(ids));
                    }
                } else {
                    var p = this.keyIdMapper.get(k);
                    if (p != null) {
                        var id = ((SingleIdWrapper) p).getId();
                        all.add(valuePool.getValue((ID) id));
                    }
                }
            }
        }
        return all;
    }

    @Override
    public void remove(K key) {
        IdWrapper<ID> cacheData;
        synchronized (keyIdMapper) {
            cacheData = keyIdMapper.get(key);
            if (cacheData == null) {
                return;
            }
            keyIdMapper.remove(key);
        }
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
            CollectionIdWrapper<ID> cacheData;

            synchronized (keyIdMapper) {
                cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
                if (cacheData == null) {
                    return;
                }
            }
            if (cacheData.getIds().remove(id)) {
                removeOrDecreaseIdUses(id);
            }
            if (cacheData.getIds().isEmpty()) {
                keyIdMapper.remove(key);
            }
        }
    }


    @Override
    public String getAlgorithmName() {
        return "LRU";
    }

    @Override
    public K getTargetKey() {
        return null;
    }

    @Override
    public void evict() {
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {
            Set<K> expiredValues;
            synchronized (keyIdMapper) {
                expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).collect(Collectors.toSet());
            }
            expiredValues.forEach(this::remove);
        }

        while (numberOfUsesById.size() >= this.actualCapacity) {
            var it = keyIdMapper.entrySet().iterator();
            K lastKey = it.next().getKey(); //get first

            if (lastKey != null) {
                remove(lastKey);
            } else {
                break;
            }
        }
    }

    @Override
    public void invalidateCache() {
        List<K> keyList;
        synchronized (keyIdMapper) {
            keyList = keyIdMapper.keySet().stream().toList();
        }
        for (K k : keyList) {
            this.remove(k);
        }

    }

    @Override
    public boolean idUsedAlready(ID v) {
        var numberOfCollectionsUsingIt = numberOfUsesById.get(v);
        return numberOfCollectionsUsingIt != null && numberOfCollectionsUsingIt > 0;
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
                    var savedIds = ((CollectionIdWrapper) keyIdMapper.get(k)).getIds();
                    var deleted = savedIds.remove(id);
                    if (deleted) {
                        if (savedIds.isEmpty()) {
                            relatedKeys.add(k);
                        }
                        removeOrDecreaseIdUses(id);
                    }
                }
            }
        }

        if (handleCollectionKeysSeparately || !returnsCollection) { //on special collection handling, a key corresponds to at most one ID
            relatedKeys.forEach(k -> {
                keyIdMapper.remove(k);
            });
        }
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
