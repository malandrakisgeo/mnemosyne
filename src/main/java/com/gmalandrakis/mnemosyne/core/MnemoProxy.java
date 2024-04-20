package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.cache.AbstractCache;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class MnemoProxy<K, V> {

    private AbstractCache<K, V> cache;
    private Method forMethod;

    public MnemoProxy(AbstractCache<K,V> cache, Method method) {
        this.cache = cache;
        this.forMethod = method;
    }


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

    //TODO: Mhpws uparxei periptwsh auto na mporei na treksei mono mia fora?
    K deduceKeyFromArgs(Method method, Object[] args) {
        var paramannot = method.getParameterAnnotations();

        int i = 0;
        outerloop:
        for (Annotation[] annotations : paramannot) {

            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(Key.class)) { //Only the first is kept! If more than one @Key-s are present, the others are dismissed
                    return (K) args[i]; //enall. break outerloop
                }
            }
            i += 1;
        }

        return (K) new CompoundKey(args); //TODO: Make sure that whenever the key is not specified, a compoundkey is always the K
    }


}
