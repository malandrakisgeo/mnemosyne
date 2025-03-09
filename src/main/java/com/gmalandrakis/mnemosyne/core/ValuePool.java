package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.structures.CacheValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ValuePool of IDs and their corresponding values.
 * A separate ValuePool is created by Mnemosyne for each object type cached, and is shared across all caches returning a type (or a Collection of it).
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class ValuePool<ID, T> {

    private final ConcurrentHashMap<ID, CacheValue<T>> valueMap = new ConcurrentHashMap<>();

    public T getValue(ID id) {
        var val = valueMap.get(id);
        if (val == null) {
            return null;
        }
        return val.getValue();
    }

    public List<T> getAll(Collection<ID> ids) {
        List<T> list = new java.util.ArrayList<>();
        ids.forEach(id -> {
            var fromValueMap = valueMap.get(id);
            if (fromValueMap != null) {
                list.add(fromValueMap.getValue());
            }
        });
        return list;
    }

    public void put(ID id, T value, boolean newCache) {
        var cachedValue = this.valueMap.get(id);
        if (cachedValue == null)
            this.valueMap.put(id, new CacheValue<>(value));
        else {
            cachedValue.updateValue(value);
            if (newCache) {
                cachedValue.increaseNumberOfUses();
            }
        }
    }

    public Map<ID, Integer> removeOrDecreaseNumberOfUsesForIds(Collection<ID> ids) {
        var numberOfUsesPerIdMap = new HashMap<ID, Integer>();
        ids.forEach(id -> {
            numberOfUsesPerIdMap.put(id, removeOrDecreaseNumberOfUsesForId(id));
        });
        return numberOfUsesPerIdMap;
    }

    public Integer removeOrDecreaseNumberOfUsesForId(ID id) {
        var cachedValue = valueMap.get(id);
        if (cachedValue == null) {
            return 0;
        }
        if (cachedValue.decreaseNumberOfUses() == 0) {
            valueMap.remove(id);
        }
        return cachedValue.getNumberOfUses();
    }

    public int getSize() {
        return valueMap.size();
    }

    public int getNumberOfUsesForId(ID id) {
        var val = valueMap.get(id);
        if (val == null) {
            return 0;
        }
        return val.getNumberOfUses();
    }

    public long getLastAccessed(ID id){
        var val = valueMap.get(id);
        if (val == null) {
            return 0;
        }
        return val.getLastAccessed();
    }

    public int getHits(ID id){
        var val = valueMap.get(id);
        if (val == null) {
            return 0;
        }
        return val.getHits();
    }

    public int getTotalOperations(ID id){
        var val = valueMap.get(id);
        if (val == null) {
            return 0;
        }
        return val.getTotalOperations();
    }

}
