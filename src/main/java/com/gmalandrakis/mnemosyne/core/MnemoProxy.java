package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.annotations.UpdateKey;
import com.gmalandrakis.mnemosyne.annotations.UpdatesCache;
import com.gmalandrakis.mnemosyne.cache.AbstractGenericCache;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.structures.AddMode;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;
import com.gmalandrakis.mnemosyne.structures.RemoveMode;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.gmalandrakis.mnemosyne.core.MnemoCommon.*;
import static com.gmalandrakis.mnemosyne.utils.GeneralUtils.allNull;
import static com.gmalandrakis.mnemosyne.utils.GeneralUtils.isAssignableTo;

/**
 * A proxy service standing between method invocations and cache implementations.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
@SuppressWarnings({"unchecked", "raw"})
public class MnemoProxy<K, ID, V> {

    protected final AbstractMnemosyneCache<K, ID, V> cache;

    private final boolean returnsCollections;

    private final boolean specialCollectionHandlingEnabled;

    private final ValuePool<ID, V> valuePool;

    private final Method cachedMethod;

    private final Object invocationTargetObject;

    private final ExecutorService executorService;

    public MnemoProxy(AbstractMnemosyneCache<K, ID, V> cache, Method method, Object invocationTargetObject,
                      ValuePool<ID, V> valuePool, boolean returnsCollections, boolean specialCollectionHandling) {
        this.cache = cache;
        this.cachedMethod = method;
        this.invocationTargetObject = invocationTargetObject;
        this.valuePool = valuePool;
        this.executorService = cache instanceof AbstractGenericCache ? ((AbstractGenericCache) cache).getInternalThreadService() : Executors.newCachedThreadPool();
        assert (!(specialCollectionHandling && !returnsCollections));
        this.returnsCollections = returnsCollections;
        this.specialCollectionHandlingEnabled = specialCollectionHandling;
    }

    Cached getAnnotation() {
        return cachedMethod.getAnnotation(Cached.class);
    }


    Object getFromCache(Object... args) {
        var compoundKey = MnemoCommon.deduceCompoundKeyFromMethodAndArgs(cachedMethod, args);
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

    Map<ID, V> getFromUnderlyingMethodAndUpdateMainCache(Object... args) {
        var compoundKey = MnemoCommon.deduceCompoundKeyFromMethodAndArgs(cachedMethod, args);

        if (!returnsCollections) {
            return getSingleAndUpdate(compoundKey, args);
        } else {
            if (specialCollectionHandlingEnabled) {
                return getMultipleSpecialAndUpdate(compoundKey, args);
            } else {
                return getMultipleAndUpdate(Collections.emptySet(), compoundKey, args);
            }
        }
    }

    Object deduce(Map<ID, V> res) {
        if (res != null && res.size() != 0) {
            if (!returnsCollections) {
                return res.values().stream().toList().get(0);
            } else {
                if (List.class.isAssignableFrom(cachedMethod.getReturnType())) {
                    return res.values().stream().collect(Collectors.toList());
                }
                if (Set.class.isAssignableFrom(cachedMethod.getReturnType())) {
                    return res.values().stream().collect(Collectors.toSet());
                }
                if (Collection.class.isAssignableFrom(cachedMethod.getReturnType())) {
                    //TODO
                    return res.values().stream().collect(Collectors.toSet());
                }
            }
        }
        return null;
    }

    void updateByRemoving(K key, Map<ID, V> idValueMap, Boolean conditionalRemove, RemoveMode removeMode) {
        if (removeMode != RemoveMode.NONE && (conditionalRemove == null || conditionalRemove)) {

            if (key != null && removeMode.equals(RemoveMode.SINGLE_VALUE) || idValueMap == null) {
                cache.remove(key);
                return;
            }

            if (returnsCollections) {
                if (removeMode.equals(RemoveMode.REMOVE_FROM_COLLECTION)) {
                    idValueMap.keySet().forEach(id -> cache.removeOneFromCollection(key, id));
                } else if (removeMode.equals(RemoveMode.REMOVE_FROM_ALL_COLLECTIONS)) {
                    cache.removeById(idValueMap.keySet());//o prwtos pou tha mou steilei email gia auto to sxolio lamvanei pente evrw.
                }
            }

            if (removeMode.equals(RemoveMode.INVALIDATE_CACHE)) {
                cache.invalidateCache();
            }
            return;
        }
    }

    void updateByAdding(K key, Map<ID, V> idValueMap, Boolean conditionalAdd, AddMode addMode) {
        if (addMode != AddMode.NONE && (conditionalAdd == null || conditionalAdd)) {

            if (addMode == AddMode.SINGLE_VALUE) {
                //cache.remove(key);
                if (returnsCollections) {
                    if (!specialCollectionHandlingEnabled) {
                        preemptiveAdd(key, idValueMap.keySet()); //Crudely written. TODO: Improve or remove. Perhaps allow the user to decide if the preemptive add happens?
                    }
                    cache.putAll(key, idValueMap.keySet());
                } else {
                    var singleKey = idValueMap.keySet().stream().toList().get(0);
                    cache.put(key, singleKey);
                }
            }

            if (addMode == AddMode.ADD_TO_COLLECTION) {
                if (returnsCollections) {
                    if (!specialCollectionHandlingEnabled) {
                        preemptiveAdd(key, idValueMap.keySet());
                    }
                }
                cache.putAll(key, idValueMap.keySet());
            }

            if (addMode == AddMode.ADD_TO_ALL_COLLECTIONS) {
                idValueMap.keySet().forEach(cache::putInAllCollections);
            }

            if (addMode == AddMode.REPLACE_EXISTING_COLLECTION) {
                cache.remove(key);
                cache.putAll(key, idValueMap.keySet());
            }
            return;
        }
    }

    ValuePool<ID, V> getValuePool() {
        return valuePool;
    }

    Method getCachedMethod() {
        return cachedMethod;
    }

    boolean isSpecialCollectionHandlingEnabled() {
        return specialCollectionHandlingEnabled;
    }

    //TODO: Simplify the flow here
    void updateCacheViaAnnotation(UpdatesCache updateCache, Annotation[][] methodParameterAnnotations, Object idOfUpdatedValue, Object updatedValue, Object... args) {
        RemoveMode removeMode = updateCache.removeMode();
        AddMode addMode = updateCache.addMode();
        var cachedMethod = this.getCachedMethod();

        if (addMode == AddMode.NONE && removeMode == RemoveMode.NONE) { //if none are set, use the underlying cache's
            //Note how we don't do the same if only one of them is set to NONE: we use the implicit conditions in that case
            removeMode = cachedMethod.getAnnotation(Cached.class).removeMode();
            addMode = cachedMethod.getAnnotation(Cached.class).addMode();
        }
        String[] targetObjectKeyNamesAndValues = updateCache.targetObjectKeys();

        var annotatedKeyNamesAndValues = getUpdateKeyNamesAndCorrespondingValues(methodParameterAnnotations, updateCache.annotatedKeys(), args);

        var targetKeyNamesAndValues = linkTargetObjectKeysToObjects(List.of(targetObjectKeyNamesAndValues), updatedValue);
        var key = getCompoundKeyForUpdate(annotatedKeyNamesAndValues, targetKeyNamesAndValues, updateCache.keyOrder(), cachedMethod, this.isSpecialCollectionHandlingEnabled());
        if (!validArgs(cachedMethod, key)) { //If the compoundKey does not correspond to the underlying arguments, there is nothing to add preemptively
            //    return; //TODO: Vres to lathos.
        }

        var explicitRemovalOnCondition = getCondition(updateCache.removeOnCondition(), annotatedKeyNamesAndValues, updatedValue, updateCache.conditionalANDGate());
        var explicitAddOnCondition = getCondition(updateCache.addOnCondition(), annotatedKeyNamesAndValues, updatedValue, updateCache.conditionalANDGate());
        var implicitRemoval = updateCache.complementaryCondition() && !explicitAddOnCondition;
        var implicitAdd = updateCache.complementaryCondition() && !explicitRemovalOnCondition;

        if (removeMode == RemoveMode.NONE && implicitRemoval) {
            removeMode = RemoveMode.values()[updateCache.addMode().ordinal()];
        }

        if (addMode == AddMode.NONE && implicitAdd) {
            addMode = AddMode.values()[updateCache.removeMode().ordinal()];
        }

        commonUpdater((ID) idOfUpdatedValue, (V) updatedValue, (K) key,
                explicitAddOnCondition || implicitAdd, explicitRemovalOnCondition || implicitRemoval,
                addMode, removeMode);
    }

    void updateCacheViaValuepoolUpdate(Object idOfUpdatedValue, Object updatedValue) {
        Cached updateCache = this.getAnnotation();

        var targetObjectKeyNamesAndValues = updateCache.targetObjectKeys();

        var targetKeyNamesAndValues = linkTargetObjectKeysToObjects(List.of(targetObjectKeyNamesAndValues), updatedValue);

        var explicitRemovalOnCondition = getCondition(updateCache.removeOnCondition(), Map.of(), updatedValue, updateCache.conditionalANDGate());
        var explicitAddOnCondition = getCondition(updateCache.addOnCondition(), Map.of(), updatedValue, updateCache.conditionalANDGate());
        var implicitRemoval = updateCache.complementaryCondition() && !explicitAddOnCondition;
        var implicitAdd = updateCache.complementaryCondition() && !explicitRemovalOnCondition;

        var removeMode = updateCache.removeMode();
        if (removeMode == RemoveMode.NONE && implicitRemoval) {
            removeMode = RemoveMode.values()[updateCache.addMode().ordinal()];
        }

        var addMode = updateCache.addMode();
        if (addMode == AddMode.NONE && implicitAdd) {
            addMode = AddMode.values()[updateCache.removeMode().ordinal()];
        }

        var key = getCompoundKeyForUpdate(null, targetKeyNamesAndValues, null, cachedMethod,
                this.isSpecialCollectionHandlingEnabled());

        commonUpdater((ID) idOfUpdatedValue, (V) updatedValue, (K) key,
                explicitAddOnCondition || implicitAdd, explicitRemovalOnCondition || implicitRemoval,
                addMode, removeMode);
    }


    private void commonUpdater(ID idOfUpdatedValue,
                               V updatedValue, K key,
                               boolean add, boolean remove,
                               AddMode addMode, RemoveMode removeMode) {

        if (idOfUpdatedValue == null && updatedValue == null) { //this can only happen on key removal.
            updateByRemoving(key, null, add, removeMode);
            return;
        }
        if (idOfUpdatedValue instanceof Map) { //This should be impossible with the new flow. TODO: Test and verify, and remove if so
            updateByRemoving(key, (Map) idOfUpdatedValue, remove, removeMode);
            updateByAdding(key, (Map) idOfUpdatedValue, add, addMode);
        } else {
            updateByRemoving(key, Map.of(idOfUpdatedValue, updatedValue), remove, removeMode);
            updateByAdding(key, Map.of(idOfUpdatedValue, updatedValue), add, addMode);
        }
    }


    LinkedHashMap<String, Object> getUpdateKeyNamesAndCorrespondingValues(Annotation[][] parameterAnnotations, String[] keyNames, Object[] args) {
        var keyNameAndValue = new LinkedHashMap<String, Object>();
        int i = 0;

        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == UpdateKey.class) {
                    var idsOfInterest = Arrays.stream(keyNames).filter(id -> id.equals(((UpdateKey) annotation).keyId())).collect(Collectors.toSet());
                    for (String id : idsOfInterest) {
                        keyNameAndValue.put(id, args[i]);
                    }
                }
            }
            i += 1;
        }

        return keyNameAndValue;
    }


    private void preemptiveAdd(Object compoundKey, Set<ID> ignore) { //HERE
        /*
          A 1-1 correspondence between keys and values is assumed for special-handling collections. But this is not the case for collections in general.
           Without this step, we may end up returning wrong data if a cached method that has never been called before with one key is updated with it:
           the underlying method is called only when the key returns null! If it returns at least one value, calling the underlying method is skipped.
           Which means that we may end up returning one value instead of e.g. fortythree if we update a non-special-handling cache without having called the underlying
           function at least once with the same key.
         */

        if (!validArgs(cachedMethod, (CompoundKey) compoundKey)) { //If the compoundKey does not correspond to the underlying arguments, there is nothing to add preemptively
            return;
        }
        var returned = cache.getAll((K) compoundKey);

        if (returned == null || returned.isEmpty()) {
            var args = deduceArgsFromCompoundKey(cachedMethod, (CompoundKey) compoundKey);
            if (args == null) {
                getMultipleAndUpdate(ignore, (CompoundKey) compoundKey, deduceArgsAsListFromCompoundKey(cachedMethod, (CompoundKey) compoundKey));
            } else {
                getMultipleAndUpdate(ignore, (CompoundKey) compoundKey, args);
            }
        }
    }

    //TODO: Improve, and unit-test. May not work properly with variable args without annotation
    private boolean validArgs(Method method, CompoundKey compoundKey) {
        var kObjects = compoundKey.getKeyObjects();
        if (kObjects.length != method.getParameterCount()) {
            return false;
        }

        var acceptable = true;
        var types = MnemoCommon.getAnnotatedParamTypes(method, Key.class);
        if (types.length == 0) {
            types = method.getParameterTypes();
        }
        for (int i = 0; i < kObjects.length; i++) {
            // kObjects.getClass().isAssignableFrom(types[i])
            acceptable &= (kObjects[i].getClass().getName().equals(types[i].getName()) || isAssignableTo(kObjects[i].getClass(), types[i]));
        }
        return acceptable;
    }


    private Object[] deduceArgsFromCompoundKey(Method method, CompoundKey compoundKey) {
        if (method.getParameters().length == 1) {
            var type = method.getParameters()[0].getType();
            if (Collection.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || List.class.isAssignableFrom(type)) {
                return null;
            }
        }
        return compoundKey.getKeyObjects();
    }

    private Object deduceArgsAsListFromCompoundKey(Method method, CompoundKey compoundKey) {
        if (method.getParameters().length == 1) {
            if (List.class.isAssignableFrom(method.getParameters()[0].getType())) {
                return Arrays.stream(compoundKey.getKeyObjects()).toList();
            }
            if (Set.class.isAssignableFrom(method.getParameters()[0].getType())) {
                return Arrays.stream(compoundKey.getKeyObjects()).collect(Collectors.toSet());
            }
        }
        return null;
    }

    private Object fetchFromSeparateHandlingCache(CompoundKey compoundKey) {
        var keys = (Collection<K>) compoundKey.getKeyObjects()[0]; //In separate-handling collection-caches, the first compoundKey is not the key itself: it contains a Collection of the actual keys instead
        var compoundKeys = (Collection<K>) keys.stream().map(k -> MnemoCommon.deduceCompoundKeyFromMethodAndArgs(this.cachedMethod, new Object[]{k})).toList(); //we need therefore to wrap each key around a compoundKey, because that is what we do everywhere else, and it will otherwise lead to a bug: a CompoundKey(value) is never equal to (value)
        var resultCollection = cache.getAll(compoundKeys);
        if (resultCollection == null || resultCollection.isEmpty() || resultCollection.size() < keys.size() || resultCollection.contains(null)) {
            return null; //We don't know which key(s) did not have a cached value. So we return null, and do the separate handling afterwards.
        }
        return resultCollection;
    }

    private Map<ID, V> getSingleAndUpdate(CompoundKey compoundKey, Object... args) {
        var value = (V) invokeUnderlyingMethod(args);
        if (value != null) {
            var id = (ID) MnemoCommon.deduceIdOrMap(value);
            valuePool.updateValueOrPutPreemptively(id, value);
            cache.put((K) compoundKey, (ID) id);
            return Map.of(id, value);
        }
        return null;
    }

    private Map<ID, V> getMultipleAndUpdate(Set<ID> ignored, CompoundKey compoundKey, Object... args) {
        /*
            This ignored set is only used for the preemptive update of collection caches (at least the non-separate-handling ones).
            If we do not ignore the IDs we want to update, depending on the cache implementation, the user may end up
            having the same IDs with two hits instead of one, potentially leading to memory leaks.
            TODO; Unit tests to verify the ignored are ignored
         */
        var value = invokeUnderlyingMethod(args);
        if (value != null) {
            assert (value instanceof Collection);
            var map = (ConcurrentMap<ID, V>) MnemoCommon.deduceIdOrMap(value);
            ignored.forEach(map::remove);
            map.forEach(valuePool::updateValueOrPutPreemptively);
            cache.putAll((K) compoundKey, map.keySet());
            return map;
        }
        return null;
    }

    //TODO: FFS, improve this cowboy-coded clusterfuck or remove the functionality altogether.
    private Map<ID, V> getMultipleSpecialAndUpdate(CompoundKey compoundKey, Object... args) {
        assert (specialCollectionHandlingEnabled && compoundKey.getKeyObjects().length == 1
                && compoundKey.getKeyObjects()[0] instanceof Collection && args.length == 1); //A very specific but very common subcase: calling a repository or rest-api method with a single Collection of IDs as argument
        // var returnTypeIsList = List.class.isAssignableFrom(cachedMethod.getReturnType());
        var keyTypeIsList = compoundKey.getKeyObjects()[0] instanceof List; // //Reminder that the Collection here may be only Set or List.
        var keys = (Collection<K>) compoundKey.getKeyObjects()[0]; //In this case, the compoundKey is not the key itself: it contains a Collection of the actual keys instead, created from the arguments given.

        List<K> failedKeys = Collections.synchronizedList(new ArrayList<K>()); //a list with the keys that did not return a value, i.e. returned empty collection or null.
        //  var keyValueMap = new ConcurrentHashMap<K, V>();
        Map<ID, V> initiallyMissedFromCache = new ConcurrentHashMap<>();
        keys.stream()
                .parallel()
                .forEach(k -> { //Note again that k is not a compoundKey!
                    var hit = (V) cache.get((K) MnemoCommon.deduceCompoundKeyFromMethodAndArgs(cachedMethod, new Object[]{k})); //reminder that (k) is never equal to CompoundKey(k), and since we wrap all (k)s around CompoundKeys everywhere else, we need to do so here too
                    if (hit == null) {
                        failedKeys.add(k);
                    } else {
                        //      keyValueMap.put(k, hit); //As noted in the documentation, a 1-1 correlation is assumed: one key corresponds to at most one value.
                        initiallyMissedFromCache.put((ID) MnemoCommon.deduceIdOrMap(hit), hit);
                    }
                });

        if (!failedKeys.isEmpty()) {
            failedKeys.stream()
                    .parallel() //Absolutely has to be parallel; unless having just a few keys, serial invocations to the underlying method will cause a hell of a delay
                    .forEach(failedKey -> {
                                var callWith = keyTypeIsList ? Collections.singletonList(failedKey) : Collections.singleton(failedKey);  //invoke with singleton List or Set.
                                var value = invokeUnderlyingMethod(callWith);
                                if (value == null) {
                                    //  keyValueMap.put(failedKey, null);
                                } else {
                                    assert (value instanceof Collection); //TODO: Add this to generalControls and delete here.
                                    var valueCollection = (Collection<V>) value;
                                    if (valueCollection.isEmpty() || allNull(valueCollection)) {
                                        //Add nothing to the cache or the result. It is apparent that the method is "null-aversive" and just ignores the values that were not found. So just do the same.
                                    } else {
                                        assert (valueCollection.size() == 1); //1-1 correlation violated otherwise! It was called with a singletonList, so at most one value is expected if we have a 1-1 correlation
                                        var id = (ID) MnemoCommon.deduceIdOrMap(valueCollection.toArray()[0]);
                                        var val = (V) valueCollection.toArray()[0];
                                        initiallyMissedFromCache.put(id, val);
                                        valuePool.updateValueOrPutPreemptively(id, val);
                                        cache.put((K) MnemoCommon.deduceCompoundKeyFromMethodAndArgs(cachedMethod, new Object[]{failedKey}), id);
                                        //  keyValueMap.put(failedKey, Iterables.get(valueCollection, 0));
                                    }
                                }
                            }
                    );
        }
        return initiallyMissedFromCache; //Reminder that only List or Set may be returned whenever separate handling is enabled.
    }

    private Object invokeUnderlyingMethod(Object... args) {

        Object updatedValue = getAnnotatedUpdatedValue(cachedMethod.getParameterAnnotations(), args); // check if any of the args is annotated as @UpdatedValue.

        if (updatedValue != null) {
            return updatedValue; // Which means that if there is any @UpdatedValue in the arguments, the result of the method is not used for updates!.
        }
        try {
            return cachedMethod.invoke(invocationTargetObject, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
