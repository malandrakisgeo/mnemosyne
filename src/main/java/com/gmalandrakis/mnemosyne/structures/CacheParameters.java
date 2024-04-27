package com.gmalandrakis.mnemosyne.structures;


import com.gmalandrakis.mnemosyne.cache.AbstractCache;

/**
 * @see com.gmalandrakis.mnemosyne.annotations.Cached Cached
 */
public class CacheParameters {

    private  Class<? extends AbstractCache> cacheType;
    private String cacheName;

    private long timeToLive;

    private int capacity;

    private long invalidationInterval;

    private int threadPoolSize;

    private boolean countdownFromCreation;

    private short preemptiveEvictionPercentage;

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getInvalidationInterval() {
        return invalidationInterval;
    }

    public void setInvalidationInterval(long invalidationInterval) {
        this.invalidationInterval = invalidationInterval;
    }

    public boolean isCountdownFromCreation() {
        return countdownFromCreation;
    }

    public void setCountdownFromCreation(boolean countdownFromCreation) {
        this.countdownFromCreation = countdownFromCreation;
    }

    public Class<? extends AbstractCache> getCacheType() {
        return cacheType;
    }

    public void setCacheType(Class<? extends AbstractCache> cacheType) {
        this.cacheType = cacheType;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public short getPreemptiveEvictionPercentage() {
        return preemptiveEvictionPercentage;
    }

    public void setPreemptiveEvictionPercentage(short preemptiveEvictionPercentage) {
        this.preemptiveEvictionPercentage = preemptiveEvictionPercentage;
    }
}
