package com.gmalandrakis.mnemosyne.utils;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

public class ParameterUtils {

    public static CacheParameters annotationValuesToCacheParameters(Cached annotation) {
        var cacheParameters = new CacheParameters();
        cacheParameters.setCacheName(annotation.cacheName());
        cacheParameters.setCapacity(annotation.capacity());
        cacheParameters.setCountdownFromCreation(annotation.countdownFromCreation());
        cacheParameters.setTimeToLive(annotation.timeToLive());
        cacheParameters.setForcedEvictionInterval(annotation.forcedEvictionInterval());
        cacheParameters.setCacheType(annotation.cacheType());
        return cacheParameters;
    }
}
