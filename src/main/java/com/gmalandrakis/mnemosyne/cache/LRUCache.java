package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRetrievalException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({"unchecked", "rawtypes"})
//WIP
public class LRUCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {    //WIP
    //WIP
    final ConcurrentLinkedQueue<K> recencyQueue = new ConcurrentLinkedQueue<>();
    ;
    final ConcurrentHashMap<ID, Integer> IdAndNumberOfCollectionsUsingIt = returnsCollection ? new ConcurrentHashMap<ID, Integer>() : null; //Meaningless to use for values with a 1-1 correspondence between keys and IDs.

    public LRUCache(CacheParameters cacheParameters, ValuePool poolService) {
        super(cacheParameters, poolService);
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
            putInCollection(key, id, value);
        } else {
            putInSingle(key, id, value);
        }

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

        if (recencyQueue.size() >= this.actualCapacity) {
            this.evict();
        }

        var possibleValue = (CollectionIdWrapper<ID>) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper<>());
        possibleValue.addAllToCollectionOrUpdate(map.keySet());

        if (!recencyQueue.contains(key)) {
            recencyQueue.add(key);
        }

        map.forEach((id, val) -> {
            var numberOfCollectionsUsingId = IdAndNumberOfCollectionsUsingIt.getOrDefault(id, 0);
            var idUsedAlready = numberOfCollectionsUsingId != 0;
            IdAndNumberOfCollectionsUsingIt.put(id, ++numberOfCollectionsUsingId);
            valuePool.put(id, val, !idUsedAlready);
        });
    }


    @Override
    public T get(K key) {
        if (!recencyQueue.contains(key)) {
            return null;
        }
        var cachedIdData = keyIdMapper.get(key);

        if (cachedIdData == null) {
            throw new RuntimeException("Key is present in LRU recency queue, buy not in keyIdMap: " + key.toString());
        }
        recencyQueue.remove(key); //send to tail
        recencyQueue.add(key);

        ID id = (ID) (handleCollectionKeysSeparately ? ((CollectionIdWrapper) cachedIdData).getIds().toArray()[0] : ((SingleIdWrapper) cachedIdData).getId());

        return valuePool.getValue(id);
    }

    @Override
    public Collection<T> getAll(K key) {
        if (!returnsCollection || !recencyQueue.contains(key)) {
            return Collections.emptyList();
        }
        var id = (CollectionIdWrapper) keyIdMapper.get(key);
        if (id == null) {
            throw new MnemosyneRetrievalException("Key is present in concurrent FIFO queue, buy not in keyIdMap: " + key.toString());
        }
        recencyQueue.remove(key); //send to tail
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
            ids.forEach(id -> {
                var numOfCollectionsUsingId = IdAndNumberOfCollectionsUsingIt.getOrDefault(id, 0) - 1;
                if (numOfCollectionsUsingId <= 0) {
                    IdAndNumberOfCollectionsUsingIt.remove(id);
                    valuePool.removeOrDecreaseNumberOfUsesForId(id);
                } else {
                    IdAndNumberOfCollectionsUsingIt.put(id, numOfCollectionsUsingId);
                }
            });
        } else {
            var id = (ID) ((SingleIdWrapper) cacheData).getId();
            valuePool.removeOrDecreaseNumberOfUsesForId(id);
        }
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
        if (timeToLive != Long.MAX_VALUE && timeToLive > 0) {
            var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey); //Io sono una anatra
            expiredValues.forEach(this::remove); //qifsha ropt
        }
        if (recencyQueue.size() >= this.actualCapacity) {
            final float eviction = Math.max((totalCapacity * evictionStepPercentage / 100f), 1); //An tuxon to evictionStepPercentage einai mhdeniko, na afairethei toulaxiston ena entry

            for (int i = 0; i < eviction; i++) {
                var lru = recencyQueue.poll(); //Gibt das erste Element zuruck und entfernt es aus der Queue
                if (lru != null) {
                    keyIdMapper.remove(lru);
                } else {
                    break; //Om queue:n är tom då finns det (nog) inget att ta bort. Dubbelkolla och kanske byta till return
                }
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
    boolean idUsedAlready(ID id) {
        //TODO

        return false;
    }

    private void putInCollection(K key, ID id, T value) {
        var keyAlreadyInThisCache = recencyQueue.contains(key);
        var idWrapper = (CollectionIdWrapper) keyIdMapper.computeIfAbsent(key, k -> new CollectionIdWrapper());

        var numberOfCollectionsUsingId = IdAndNumberOfCollectionsUsingIt.getOrDefault(id, 0);
        var idUsedAlready = numberOfCollectionsUsingId != 0;
        IdAndNumberOfCollectionsUsingIt.put(id, ++numberOfCollectionsUsingId);

        idWrapper.addToCollectionOrUpdate(id);

        if (!keyAlreadyInThisCache) {
            recencyQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
        valuePool.put(id, value, !idUsedAlready);
    }

    private void putInSingle(K key, ID id, T value) {
        var keyAlreadyInThisCache = recencyQueue.contains(key);
        var existentIdWrapperForKey = keyIdMapper.get(key);

        if (existentIdWrapperForKey == null) {
            keyIdMapper.put(key, new SingleIdWrapper<>(id));
        }

        if (!keyAlreadyInThisCache) {
            recencyQueue.add(key); //reminder that updates are not synonymous to accesses, and this is why we do not change the position in the queue on updating.
        }
        valuePool.put(id, value, !keyAlreadyInThisCache);
    }
}
