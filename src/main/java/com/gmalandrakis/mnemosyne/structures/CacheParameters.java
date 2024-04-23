package com.gmalandrakis.mnemosyne.structures;


import com.gmalandrakis.mnemosyne.cache.AbstractCache;

/**
 * @see com.gmalandrakis.mnemosyne.annotations.Cached Cached
 */
public class CacheParameters {

    private  Class<? extends AbstractCache> cacheType;
    private String cacheName;

    private long timeToLive;

    private long capacity;

    private long forcedEvictionInterval;

    private boolean countdownFromCreation;

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

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getForcedEvictionInterval() {
        return forcedEvictionInterval;
    }

    public void setForcedEvictionInterval(long forcedEvictionInterval) {
        this.forcedEvictionInterval = forcedEvictionInterval;
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
}
