package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * A proxy service standing between method invocations and cache implementations.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class MnemoProxy<K, V> {

    private final AbstractMnemosyneCache<K, V> cache;
    private final Method forMethod;

    public MnemoProxy(AbstractMnemosyneCache<K,V> cache, Method method) {
        this.cache = cache;
        this.forMethod = method;
    }

    @SuppressWarnings("unchecked")
    public V invoke(Object obj, Object... args) throws Throwable {
        var key = deduceKeyFromArgs(forMethod, args);
        var value = cache.get(key);
        if (value == null ) {
            var val = forMethod.invoke(obj, args);
            if (val != null) {
                cache.put(key, (V) val);
                return (V) val;
            }
        }
        return value;
    }
    
    @SuppressWarnings("unchecked")
    K deduceKeyFromArgs(Method method, Object[] args) {
        var paramannot = method.getParameterAnnotations();

        int i = 0;
        outerloop:
        for (Annotation[] annotations : paramannot) {

            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(Key.class)) { //Only the first is kept! If more than one @Key-s are present, the others are disregarded
                    return (K) args[i]; //enall. break outerloop
                }
            }
            i += 1;
        }

        return (K) new CompoundKey(args); //TODO: Make sure that whenever the key is not specified, a compoundkey is always the K
    }


}
