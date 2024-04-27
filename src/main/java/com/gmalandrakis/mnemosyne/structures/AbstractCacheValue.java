package com.gmalandrakis.mnemosyne.structures;

/**
 * @param <V> The type of the cached value.
 * @see GenericCacheValue
 */
public abstract class AbstractCacheValue<V> {

    long lastAccessed;
    long createdOn;
    int hits;
    V value;

    AbstractCacheValue(V value) {
        this.createdOn = this.lastAccessed = System.currentTimeMillis();
        this.value = value;
        hits = 1;
    }

    public abstract V get() ;

    public abstract void update(V updated) ;

    public long getLastAccessed() {
        return lastAccessed;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public int getHits() {
        return hits;
    }


}
