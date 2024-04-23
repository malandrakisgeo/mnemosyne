package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.cache.AbstractCache;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.lang.reflect.Method;
import java.util.HashMap;

import static com.gmalandrakis.mnemosyne.utils.ParameterUtils.annotationValuesToCacheParameters;

public class MnemoService {
    private HashMap<Method, MnemoProxy<?, ?>> proxies = new HashMap<>();

    AbstractCache<?, ?> createCache(CacheParameters parameters) {
        return null;
    }

    private MnemoProxy generateInternal(Method method, Cached annotation) {
        if (annotation == null || method == null) {
            return null;
        }
        try {
            var cacheParams = annotationValuesToCacheParameters(annotation);
            var algoClass = cacheParams.getCacheType();
            AbstractCache cache = algoClass.getDeclaredConstructor(CacheParameters.class).newInstance(cacheParams);
            var proxyService = new MnemoProxy<>(cache, method);
            proxies.put(method, proxyService);
            return proxyService;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MnemoProxy generateForMethod(Method method) {
        var anno = method.getAnnotation(Cached.class);
        if (anno != null) {
            return generateInternal(method, anno);
        }
        return null;
    }


    public void generateForClass(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            generateForMethod(method);
        }
    }

    public HashMap<Method, MnemoProxy<?, ?>> getProxies() {
        return proxies;
    }
}
