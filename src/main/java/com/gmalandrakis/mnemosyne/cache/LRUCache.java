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
    //This isn't a proper LRU. I don't know what I had in my mind when I called this an LRU policy. TODO: Replace with a proper LRU

    final LinkedList<K> keyOrder = new LinkedList<>();
    final Object lock = new Object(); //TODO: Replace with reentrant lock

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

        var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
        possibleValue.addAllToCollectionOrUpdate(map);
        updateKeyOrderOnInsertion(key);

        map.forEach(this::addOrUpdateIdAndValue);
    }

    @Override
    public void putInAllCollections(ID id) {
        if (!returnsCollection || handleCollectionKeysSeparately) {
            return;
        }
        var in = numberOfUsesById.get(id);
        var initialNumOfUses = in == null ? 0 : in;
        int i = initialNumOfUses;
        synchronized (lock) {
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
        updateKeyOrderOnInsertion(key);
        addOrUpdateIdAndValue(id);
    }

    @Override
    public T get(K key) {
        var val = keyIdMapper.get(key);
        if (val == null) {
            return null;
        }
        if (!countdownFromCreation) {
            moveToTail(key);
        }
        //TODO: Perhaps a cacheIdData with single Id could be used when handleCollectionKeysSeparately.
        ID id = (ID) (handleCollectionKeysSeparately ? ((CollectionIdWrapper) val).getIds().toArray()[0] : ((SingleIdWrapper) val).getId()); //EDW: dunhtika buggara
        //  var id = ((SingleIdWrapper) val).getId();
        return valuePool.getValue((ID) id);

    }

    @Override
    public Collection<T> getAll(K key) {
        if (!returnsCollection || !keyIdMapper.containsKey(key)) {
            return Collections.emptyList();
        }
        var ids = ((CollectionIdWrapper) keyIdMapper.get(key));
        if (!countdownFromCreation) {
            moveToTail(key);
        }
        if (ids != null) {
            return valuePool.getAll(ids.getIds());
        }
        return Collections.emptyList();
    }

    @Override

    public Collection<T> getAll(Collection<K> key) {
        var all = new HashSet<T>();
        for (K k : key) {
            if (returnsCollection) {
                all.addAll(getAll(k));
            } else {
                var res = get(k);
                if (res != null) {
                    all.add(get(k));
                }
            }
        }

        return all;
    }

    @Override
    public void remove(K key) {
        IdWrapper<ID> cacheData;
        cacheData = keyIdMapper.get(key);
        if (cacheData == null) {
            return;
        }
        synchronized (lock) {
            keyIdMapper.remove(key);
            keyOrder.remove(key);
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

            cacheData = (CollectionIdWrapper) keyIdMapper.get(key);
            if (cacheData == null) {
                return;
            }

            if (cacheData.getIds().remove(id)) {
                removeOrDecreaseIdUses(id);
            }
            if (cacheData.getIds().isEmpty()) {
                synchronized (lock) {
                    keyIdMapper.remove(key);
                    keyOrder.remove(key);
                }
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
            Set<K> expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey).collect(Collectors.toSet());
            expiredValues.forEach(this::remove);
        }

        while (numberOfUsesById.size() >= this.actualCapacity) {
            synchronized (lock) {
                remove(keyOrder.getFirst());  //If an NPE or NSE occurs here, the bug is deeper.
            }
        }
    }

    @Override
    public void invalidateCache() {
        List<K> keyList;
        keyList = keyIdMapper.keySet().stream().toList();

        for (K k : keyList) {
            this.remove(k);
        }
        assert (keyOrder.isEmpty()); //TODO: Perhaps throw exception with message if this happens
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
            synchronized (lock) {
                relatedKeys.forEach(k -> {
                    keyIdMapper.remove(k);
                    keyOrder.remove(k);
                });
            }
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

    private void updateKeyOrderOnInsertion(K key) {
        synchronized (lock) {
            if (!countdownFromCreation) { //access-defined LRU
                if (!keyOrder.contains(key)) {
                    keyOrder.add(key);
                }
            } else {
                keyOrder.remove(key); //remove if present
                keyOrder.add(key);
            }
        }
    }

    private void moveToTail(K key) {
        synchronized (lock) {
            keyOrder.remove(key);
            keyOrder.add(key);
        }
    }

}
