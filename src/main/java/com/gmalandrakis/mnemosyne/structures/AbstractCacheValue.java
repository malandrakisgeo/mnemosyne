package com.gmalandrakis.mnemosyne.structures;

/**
 * An abstraction of a wrapper-class for the caches values, storing metadata like access-time.
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

    /**
     * Returns the stored value
     */
    public abstract V get();

    public abstract void update(V updated);

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
