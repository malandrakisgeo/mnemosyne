package com.gmalandrakis.mnemosyne.cache.old;

public class GenericCacheValue<V> extends AbstractCacheValue<V> {

    public GenericCacheValue(V value) {
        super(value);
    }

    public V get() {
        hits += 1;
        this.lastAccessed = System.currentTimeMillis();
        return value;
    }

    public void update(V updated) {
        value = updated;
        this.lastAccessed = System.currentTimeMillis();
    }

}
