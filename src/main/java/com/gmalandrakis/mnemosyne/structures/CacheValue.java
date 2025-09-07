package com.gmalandrakis.mnemosyne.structures;

/**
 * A wrapper for cached values along with basic metadata.
 *
 * @param <T> The type of the cached value
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class CacheValue<T> {
    private int cachesUsingValue;
    private long createdOn;
    private long lastUpdated;
    private T value;


    public CacheValue(T t, boolean addedPreemptively) {
        createdOn = System.currentTimeMillis();
        value = t;
        cachesUsingValue = addedPreemptively ? 0 : 1; //manually increased or decreased afterwards by the ValuePool. We set it to zero if the CacheValue was created preemptively (i.e. not for a particular cache)
    }

    public CacheValue(T t) {
        createdOn = System.currentTimeMillis();
        value = t;
        cachesUsingValue =  1; //manually increased or decreased afterwards by the ValuePool. We set it to zero if the CacheValue was created preemptively (i.e. not for a particular cache)
    }

    public synchronized int increaseNumberOfUses() {
        return ++cachesUsingValue;
    }

    public synchronized int decreaseNumberOfUses() {
        return --cachesUsingValue;
    }

    public T getValue() {
        return value;
    }

    public int getNumberOfUses() {
        return cachesUsingValue;
    }

    public synchronized void updateValue(T t) {
        lastUpdated = System.currentTimeMillis();
        this.value = t;
    }


    public long getCreatedOn() {
        return createdOn;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}
