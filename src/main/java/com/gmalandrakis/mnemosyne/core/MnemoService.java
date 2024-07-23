package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;

import java.lang.reflect.Method;
import java.util.HashMap;

import static com.gmalandrakis.mnemosyne.utils.ParameterUtils.annotationValuesToCacheParameters;

/**
 * A service class used by {@link com.gmalandrakis.mnemosyne.spring.SpringInterceptor SpringInterceptor}.
 * It generates {@link com.gmalandrakis.mnemosyne.core.MnemoProxy proxy objects} for all the intercepted methods and stores them in a HashMap.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class MnemoService {
    private HashMap<Method, MnemoProxy<?, ?>> proxies = new HashMap<>();

    AbstractMnemosyneCache<?, ?> createCache(CacheParameters parameters) {
        return null;
    }

    private MnemoProxy generateInternal(Method method, Cached annotation) {
        if (annotation == null || method == null) {
            return null;
        }
        try {
            var cacheParams = annotationValuesToCacheParameters(annotation);
            var algoClass = cacheParams.getCacheType();
            AbstractMnemosyneCache cache = algoClass.getDeclaredConstructor(CacheParameters.class).newInstance(cacheParams);
            var proxyService = new MnemoProxy<>(cache, method);
            proxies.put(method, proxyService);
            return proxyService;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MnemoProxy generateForMethod(Method method) {
        var annotation = method.getAnnotation(Cached.class);
        if (annotation != null) {
            return generateInternal(method, annotation);
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
