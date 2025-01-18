package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.UpdateCache;
import com.gmalandrakis.mnemosyne.cache.AbstractGenericCache;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;
import com.google.common.collect.Iterables;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings({"unchecked", "raw"})
public class MnemoProxy<K, ID, V> {

    private final AbstractMnemosyneCache<K, ID, V> cache;

    private final boolean returnsCollections;

    private final boolean specialCollectionHandlingEnabled;

    private final ValuePool<ID, V> valuePool;

    private final Method forMethod;

    private final ExecutorService executorService;

    public MnemoProxy(AbstractMnemosyneCache<K, ID, V> cache, Method method, ValuePool<ID, V> valuePool, boolean returnsCollections, boolean specialCollectionHandling) {
        this.cache = cache;
        this.forMethod = method;
        this.valuePool = valuePool;
        this.executorService = cache instanceof AbstractGenericCache ? ((AbstractGenericCache) cache).getInternalThreadService() : Executors.newCachedThreadPool();
        assert (!(specialCollectionHandling && !returnsCollections));
        this.returnsCollections = returnsCollections;
        this.specialCollectionHandlingEnabled = specialCollectionHandling;
    }


    Object getFromCache(Object... args) {
        var compoundKey = GeneralUtils.deduceCompoundKeyFromMethodAndArgs(forMethod, args);
        if (returnsCollections) {
            if (specialCollectionHandlingEnabled) {
                return fetchFromSeparateHandlingCache(compoundKey);
            }
            var cachedValue = cache.getAll((K) compoundKey);
            if (cachedValue == null || cachedValue.isEmpty()) {
                return null;
            }
            return cachedValue;
        } else {
            return cache.get((K) compoundKey);
        }
    }

    Object getFromUnderlyingMethodAndUpdateMainCache(Object invocationTargetObject, Object... args) {
        var compoundKey = GeneralUtils.deduceCompoundKeyFromMethodAndArgs(forMethod, args);

        if (!returnsCollections) {
            return getSingle(compoundKey, invocationTargetObject, args);
        } else {
            if (specialCollectionHandlingEnabled) {
                return getMultipleSpecial(compoundKey, invocationTargetObject, args);
            } else {
                return getMultiple(compoundKey, invocationTargetObject, args);
            }
        }
    }

    //Keep in mind that we don't need to update the valuePool again. It is already updated by the handling of UpdatedValue-annotation
    void updateCacheKeysAndIds(K key, Map<ID, V> idValueMap, UpdateCache updateCache) {
        if (key == null || idValueMap == null || updateCache == null) {
            return;
        }
       // this.executorService.execute(() -> { //TODO: Decide whether this should run in another thread to avoid delays
            if (updateCache.removeValueFromCollection()) {
                //TODO
            }

            if (updateCache.removeValueFromAllCollections()) {
                //todo
            }

            if (updateCache.addIfAbsent()) {
                if (cache.get(key) == null)
                    if (returnsCollections) {
                        cache.putAll(key, idValueMap);
                    } else {
                        var singleKey = idValueMap.keySet().stream().toList().get(0);
                        cache.put(key, singleKey, valuePool.getValue(singleKey));
                    }
            }
        //});
    }

    AbstractMnemosyneCache<K, ID, V> getCache() {
        return cache;
    }

    ValuePool<ID, V> getValuePool() {
        return valuePool;
    }

    private Object fetchFromSeparateHandlingCache(CompoundKey compoundKey) {
        var keys = (List<K>) compoundKey.getKeyObjects()[0]; //In this case, the compoundKey is not the key itself: it contains a Collection of the actual keys instead, and it is its' sole argument
        var resultCollection = cache.getAll(keys);
        if (resultCollection == null || resultCollection.isEmpty() || resultCollection.contains(null) || resultCollection.size() < keys.size()) {
            return null; //We don't know which key did not have a cached value. So we return null, and do the separate handling afterwards.
        }
        return resultCollection;
    }

    private V getSingle(CompoundKey compoundKey, Object invocationTargetObject, Object... args) {
        var value = (V) invokeUnderlyingMethod(invocationTargetObject, args);
        if (value != null) {
            var id = (ID) GeneralUtils.deduceId(value);
            cache.put((K) compoundKey, (ID) id, value);
        }
        return value;
    }

    private Collection<V> getMultiple(CompoundKey compoundKey, Object invocationTargetObject, Object... args) {
        var value = invokeUnderlyingMethod(invocationTargetObject, args);
        if (value != null) {
            assert (Collection.class.isAssignableFrom(value.getClass()));
            var valueCollection = (Collection<V>) value;
            var map = (Map<ID, V>) GeneralUtils.deduceId(value);
            cache.putAll((K) compoundKey, map);
            return valueCollection;
        }
        return null;
    }

    //TODO: FFS, improve this cowboy-coded clusterfuck or remove it altogether.
    private Collection<V> getMultipleSpecial(CompoundKey compoundKey, Object invocationTargetObject, Object... args) {
        assert (specialCollectionHandlingEnabled && compoundKey.getKeyObjects().length == 1
                && Collection.class.isAssignableFrom(compoundKey.getKeyObjects()[0].getClass()) && args.length == 1); //A very specific but very common subcase: calling a repository or rest-api method with a single Collection of IDs as argument
        var returnTypeIsList = List.class.isAssignableFrom(forMethod.getReturnType());
        var keyTypeIsList = List.class.isAssignableFrom(compoundKey.getKeyObjects()[0].getClass()); //Reminder that the Collection here may be only Set or List.
        var keys = (List<K>) compoundKey.getKeyObjects()[0]; //In this case, the compoundKey is not the key itself: it contains a Collection of the actual keys instead, created from the arguments given, treated here as List.
        List<K> failedKeys = Collections.synchronizedList(new ArrayList<K>()); //a list with the keys that did not return a value, i.e. returned empty collection or null.
        var totalResult = new ConcurrentHashMap<K, V>();

        keys.stream()
                .parallel()
                .forEach(k -> { //Note again that k is not a compoundKey!
                    var hit = (V) cache.get(k);
                    if (hit == null) {
                        failedKeys.add(k);
                    } else {
                        totalResult.put(k, hit); //As noted in the documentation, a 1-1 correlation is assumed: one key corresponds to at most one value.
                    }
                });

        if (!failedKeys.isEmpty()) {
            failedKeys.stream()
                    .parallel() //Absolutely has to be parallel; unless having just a few keys, serial invocations to the underlying method will cause a hell of a delay
                    .forEach(failedKey -> {
                                var callWith = keyTypeIsList ? Collections.singletonList(failedKey) : Collections.singleton(failedKey);  //invoke with singleton List or Set.
                                var value = invokeUnderlyingMethod(invocationTargetObject, callWith);
                                if (value == null) {
                                    totalResult.put(failedKey, null);
                                } else {
                                    assert (Collection.class.isAssignableFrom(value.getClass())); //TODO: Add this to generalControls and delete here.
                                    var valueCollection = (Collection<V>) value;
                                    if (valueCollection.isEmpty()) {
                                        //Add nothing to the cache or the result. It is apparent that the method is "null-aversive" and just ignores the values that were not found. So just do the same.
                                    } else {
                                        assert (valueCollection.size() == 1); //1-1 correlation violated otherwise! It was called with a singletonList, so at most one value is expected if we have a 1-1 correlation
                                        cache.put(failedKey, (ID) GeneralUtils.deduceId(valueCollection.toArray()[0]), (V) valueCollection.toArray()[0]);
                                        totalResult.put(failedKey, Iterables.get(valueCollection, 0));
                                    }
                                }
                            }
                    );
        }
        return returnTypeIsList ? totalResult.values().stream().toList() : totalResult.values(); //Reminder that only List or Set may be returned whenever separate handling is enabled.
    }

    private Object invokeUnderlyingMethod(Object invocationTargetObject, Object... args) {
        try {
            return forMethod.invoke(invocationTargetObject, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
