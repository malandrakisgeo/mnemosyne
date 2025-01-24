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

@SuppressWarnings({"unchecked", "rawtypes"})
//WIP
public class LRUCacheold<K, ID, T> extends AbstractGenericCache<K, ID, T> {    //WIP
    //WIP
    final ConcurrentLinkedQueue<K> recencyQueue  = new ConcurrentLinkedQueue<>();;
    final ConcurrentHashMap<ID, Integer> IdAndNumberOfCollectionsUsingIt = returnsCollection ? new ConcurrentHashMap<ID, Integer>() : null; //Meaningless to use for values with a 1-1 correspondence between keys and IDs.

    public LRUCacheold(CacheParameters cacheParameters, ValuePool poolService) {
        super(cacheParameters, poolService);
    }

    @Override
    public void put(K key, ID id, T value) {
        if (key == null || value == null) {
            return;
        }
        if (recencyQueue.size() >= actualCapacity) {
            this.evict(); //Ideally, this statement is never reached, and evict() is called by the internal threads before the size exceeds the capacity. But if multiple threads write concurrently in the cache, they will likely prevent the internal threads from evicting it.
        }
        var existentIdWrapperForKey = keyIdMapper.get(key);
        if (returnsCollection) {
            if (existentIdWrapperForKey != null) {
                ((CollectionIdWrapper) existentIdWrapperForKey).addToCollectionOrUpdate(id);
            } else {
                keyIdMapper.put(key, new CollectionIdWrapper<>(Collections.singleton(id)));
            }
        } else {
            final ConcurrentHashMap<K, IdWrapper<ID>> keyIdMap;
            valuePool.put(id, value, !idUsedAlready(id));
        }
        recencyQueue.remove(key); //send to tail
        recencyQueue.add(key);

    }

    @Override
    public void putAll(K key, Map<ID, T> ids) {

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
        keyIdMapper.remove(key);
        recencyQueue.remove(key);
        if (returnsCollection) {
            var ids = ((CollectionIdWrapper) cacheData).getIds();
            valuePool.removeOrDecreaseNumberOfUsesForIds(ids);
        } else {
            var id = (ID) ((SingleIdWrapper) cacheData).getId();
            valuePool.removeOrDecreaseNumberOfUsesForId(id);
        }
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
                recencyQueue.remove(key);
            }
            valuePool.removeOrDecreaseNumberOfUsesForId(id);
        }
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
        var expiredValues = keyIdMapper.entrySet().stream().filter(this::isExpired).map(Map.Entry::getKey); //Io sono una anatra
        expiredValues.forEach(this::remove); //qifsha ropt

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
}
