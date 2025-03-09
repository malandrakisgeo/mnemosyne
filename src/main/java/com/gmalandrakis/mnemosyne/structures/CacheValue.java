package com.gmalandrakis.mnemosyne.structures;

/**
 * A wrapper for cached values  along with metadata such as creation time, last access time, last update time, and number of hits.
 * <p>
 * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, fields like
 * lastAccessed, createdOn, hits, etc, are stored and retrieved via {@link com.gmalandrakis.mnemosyne.structures.IdWrapper IdWrapper} and its' implementations.
 * This is the recommended practice for ID-centered caching algorithms, to skip the extra step of finding the CacheValue before accessing critical metadata
 * (e.g. timestamp of last access to remove the oldest entries on a cache memory overflow).
 * But this is entirely up to the developer.
 *
 * @param <T> The type of the cached value
 *            <p>
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class CacheValue<T> {
    private int cachesUsingValue;
    private long lastAccessed;
    private long createdOn; //todo: Utreda möjligheten att byta om alla long och int mot AtomicInteger och AtomicLong
    private long lastUpdated;
    private int hits;
    private int totalOperations;
    private T value;

    public CacheValue(T t) {
        createdOn = System.currentTimeMillis();
        value = t;
        hits = 0;
        totalOperations = 0;
        cachesUsingValue = 1; //manually increased or decreased afterwards by the ValuePool
    }

    public synchronized int increaseNumberOfUses() {
        return ++cachesUsingValue;
    }

    public synchronized int decreaseNumberOfUses() {
        return --cachesUsingValue;
    }

    public T getValue() {
        synchronized(this) { //Das ist ein schlechtes practice. Den eprepe na xreiazetai.
            lastAccessed = System.currentTimeMillis();
            ++hits;
            ++totalOperations;
        }
        return value;
    }

    public int getNumberOfUses() {
        return cachesUsingValue;
    }

    public synchronized void updateValue(T t) {
        lastUpdated = System.currentTimeMillis();
        ++totalOperations;
        this.value = t;
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public int getHits() {
        return hits;
    }

    public int getTotalOperations() {
        return totalOperations;
    }
}
