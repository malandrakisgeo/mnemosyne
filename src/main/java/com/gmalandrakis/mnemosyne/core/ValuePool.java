package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.structures.CacheValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        }
        if (newCache) {
            this.valueMap.get(id).increaseNumberOfUses();
        }
    }

    public Map<ID, Integer> removeOrDecreaseNumberOfUsesForIds(Collection<ID> ids) {
        var numberOfUsesPerIdMap = new HashMap<ID, Integer>();
        ids.forEach(id->{
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
        return this.valueMap.size();
    }

}
