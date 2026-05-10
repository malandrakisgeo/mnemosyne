package com.gmalandrakis.mnemosyne.utils;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

public class ParameterUtils {

    //TODO: See if this really is necessary. We most likely can just use the annotation itself instead?
    public static CacheParameters annotationValuesToCacheParameters(Cached annotation, boolean returnsCollection) {
        var cacheParameters = new CacheParameters();
        cacheParameters.setCacheName(annotation.cacheName());
        cacheParameters.setCapacity(annotation.capacity());
        cacheParameters.setCountdownFromCreation(annotation.countdownFromCreation());
        cacheParameters.setTimeToLive(annotation.timeToLive());
        cacheParameters.setInvalidationInterval(annotation.invalidationInterval());
        cacheParameters.setCacheType(annotation.cacheType());
        cacheParameters.setThreadPoolSize(annotation.threadPoolSize());
        cacheParameters.setPreemptiveEvictionPercentage(annotation.preemptiveEvictionPercentage());
        cacheParameters.setEvictionStepPercentage(annotation.evictionStepPercentage());
        cacheParameters.setHandleCollectionKeysSeparately(annotation.allowSeparateHandlingForKeyCollections());
        cacheParameters.setReturnsCollection(returnsCollection);
        return cacheParameters;
    }
}
