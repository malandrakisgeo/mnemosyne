package com.gmalandrakis.mnemosyne.structures;


import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;

/**
 * @see com.gmalandrakis.mnemosyne.annotations.Cached Cached
 */
public class CacheParameters {

    private static final int MAX_MAP_SIZE = 1 << 30 - 1;
    private static final int DEFAULT_MAP_SIZE = 16;

    private Class<? extends AbstractMnemosyneCache> cacheType;
    private String cacheName;
    private long timeToLive;
    private int capacity;
    private long invalidationInterval;
    private int threadPoolSize;
    private boolean countdownFromCreation;
    private short preemptiveEvictionPercentage;
    private short evictionStepPercentage;
    private boolean handleCollectionKeysSeparately;
    private boolean returnsCollection;


    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public long getTimeToLive() {
        return (timeToLive <= 0 ? Long.MAX_VALUE : timeToLive);
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
    }

    public int getCapacity() {
        return  (capacity < 0 ? MAX_MAP_SIZE : ( capacity == 0 ? DEFAULT_MAP_SIZE : capacity));
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getInvalidationInterval() {
        return (invalidationInterval < 0 ? Long.MAX_VALUE : invalidationInterval);
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

    public Class<? extends AbstractMnemosyneCache> getCacheType() {
        return cacheType;
    }

    public void setCacheType(Class<? extends AbstractMnemosyneCache> cacheType) {
        this.cacheType = cacheType;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public short getPreemptiveEvictionPercentage() {
       return preemptiveEvictionPercentage = (preemptiveEvictionPercentage <= 0 || preemptiveEvictionPercentage >= 100 ? 100 : preemptiveEvictionPercentage);
    }

    public void setPreemptiveEvictionPercentage(short preemptiveEvictionPercentage) {
        this.preemptiveEvictionPercentage = preemptiveEvictionPercentage;
    }

    public short getEvictionStepPercentage() {
        return (evictionStepPercentage < 0 || evictionStepPercentage > 100) ? 0 : evictionStepPercentage;
    }

    public void setEvictionStepPercentage(short evictionStepPercentage) {
        this.evictionStepPercentage = evictionStepPercentage;
    }

    public boolean isHandleCollectionKeysSeparately() {
        return handleCollectionKeysSeparately;
    }

    public void setHandleCollectionKeysSeparately(boolean handleCollectionKeysSeparately) {
        this.handleCollectionKeysSeparately = handleCollectionKeysSeparately;
    }

    public boolean isReturnsCollection() {
        return returnsCollection;
    }

    public void setReturnsCollection(boolean returnsCollection) {
        this.returnsCollection = returnsCollection;
    }
}
