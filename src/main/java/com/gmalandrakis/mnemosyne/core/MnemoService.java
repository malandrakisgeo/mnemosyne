package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.*;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.exception.MnemosyneInitializationException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRuntimeException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneUpdateException;
import com.gmalandrakis.mnemosyne.structures.AddMode;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.RemoveMode;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.gmalandrakis.mnemosyne.core.MnemoCommon.*;
import static com.gmalandrakis.mnemosyne.utils.ParameterUtils.annotationValuesToCacheParameters;

/**
 * Generates {@link MnemoProxy proxy objects} for all the cached methods
 * and stores them in a HashMap,
 * implements the logic behind cache requests and methods request, and updates the target caches on cache miss,
 * and updates all necessary caches on {@link UpdatesCache @UpdateCache}
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class MnemoService {
    private final ConcurrentHashMap<ValuePool, List<MnemoProxy>> proxiesByValuePool = new ConcurrentHashMap<>(); //pools by fully qualified object name

    private final ConcurrentHashMap<String, ValuePool> valuePoolConcurrentHashMap = new ConcurrentHashMap<>(); //pools by fully qualified object name
    private final ConcurrentHashMap<Method, MnemoProxy> proxies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MnemoProxy> cachesByName = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public Object invokeMethodAndUpdateValuePool(Method method, Object obj, Object... args) {
        Object object = null;
        try {
            object = method.invoke(obj, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        Object updatedValue = getAnnotatedUpdatedValue(method.getParameterAnnotations(), args); // check if any of the args is annotated as @UpdatedValue.

        if (updatedValue != null) {
            object = updatedValue; // Which means that if there is any @UpdatedValue in the arguments, the result of the method is not used for updates!.
        }
        var id = MnemoCommon.deduceIdOrMap(object);

        var annotation = method.getAnnotation(UpdatesValuePool.class);
        assert (annotation != null);
        //Update the value pool with latest version of the object (or remove it, along with all the IDs from the proxies)
        if (id instanceof Map) {
            updateValuePool(method, (Map) id, annotation.remove(), annotation.addIfAbsent());
        } else {
            updateValuePool(method, Map.of(id, object), annotation.remove(), annotation.addIfAbsent());
        }
        var vp = getValuePool(method);
        var v = proxiesByValuePool.get(vp);
        if (v != null && !v.isEmpty() && !annotation.remove()) { //If we remove, we remove via updateValuePool.
            for (MnemoProxy mnemoProxy : v) {
                mnemoProxy.updateCacheViaValuepoolUpdate(id, object); //Otherwise, we add/update on conditions
            }
        }
        return object;
    }

    public Object invokeMethodAndUpdate(Method method, Object obj, Object... args) {
        Object object = null;
        try {
            object = method.invoke(obj, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        Object updatedValue = getAnnotatedUpdatedValue(method.getParameterAnnotations(), args); // check if any of the args is annotated as @UpdatedValue.

        if (updatedValue != null) {
            object = updatedValue; // Which means that if there is any @UpdatedValue in the arguments, the result of the method is not used for updates!.
        }
        var id = MnemoCommon.deduceIdOrMap(object);
        if (id instanceof Map) {
            updateValuePool(method, (Map) id, false, false);
            updateRelatedCaches(method, (Map) id, args);
        } else {
            updateValuePool(method, Map.of(id, object), false, false);
            updateRelatedCaches(method, Map.of(id, object), args);
        }

        return object;
    }

    public Object fetchFromCacheOrInvokeMethodAndUpdate(Method method, Object... args) {
        var cacheProxy = proxies.get(method);
        assert (cacheProxy != null);
        Object result = tryFetchFromCache(cacheProxy, args);
        if (result == null) {
            var idValMap = cacheProxy.getFromUnderlyingMethodAndUpdateMainCache(args);
            updateRelatedCaches(method, idValMap, args);
            result = cacheProxy.deduce(idValMap);
        }
        return result;
    }

    public List<MnemoProxy> generateCachesForBean(Object singletonBean) {
        var clazz = singletonBean.getClass();

        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(Cached.class) != null)
                .map(m -> this.generateForMethod(m, singletonBean))
                .toList();
    }

    public void generateUpdatesForBean(Object singletonBean) {
        var clazz = singletonBean.getClass();
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(UpdatesCache.class) != null)
                .forEach(this::generateUpdaterForMethod);
    }

    MnemoProxy generateForMethod(Method method, Object singletonBean) {
        var annotation = method.getAnnotation(Cached.class);
        if (annotation != null) {
            return generateInternal(method, singletonBean);
        }
        return null;
    }

    void generateUpdaterForMethod(Method method) {
        var annotation = method.getAnnotation(UpdatesCache.class);
        if (annotation != null) {
            updateControls(method, annotation);
        }
    }

    Object tryFetchFromCache(MnemoProxy cacheProxy, Object... args) { //Used for improved testability
        assert (cacheProxy != null);
        return cacheProxy.getFromCache(args);
    }

    //TODO: Unit test
    ValuePool getValuePool(Method method) {
        var cleanType = updateType(method);

        var vp = valuePoolConcurrentHashMap.get(cleanType);
        if (vp == null) {
            throw new MnemosyneRuntimeException("Value pool not found for method " + method.getName());
        }
        return vp;
    }

    ValuePool getOrCreateValuePool(Method method) {
        var cleanType = getCleanType(method);
        var vp = valuePoolConcurrentHashMap.computeIfAbsent(cleanType, k -> new ValuePool<>());

        return vp;
    }

    private void updateRelatedCaches(Method method, Map<?, ?> idValMap, Object... args) {
        //   threadPool.execute(() -> {
        var updatesCaches = method.getAnnotation(UpdatesCaches.class);
        if (updatesCaches != null) {
            for (UpdatesCache updateCache : updatesCaches.value()) {
                MnemoProxy cacheToBeUpdated = this.cachesByName.get(updateCache.name());
                idValMap.forEach((id, v) -> {
                    cacheToBeUpdated.updateCacheViaAnnotation(updateCache, method.getParameterAnnotations(), id, v, args);
                });
            }
        }
        var updateCache = method.getAnnotation(UpdatesCache.class);
        if (updateCache != null) {
            MnemoProxy cacheToBeUpdated = this.cachesByName.get(updateCache.name());

            idValMap.forEach((id, v) -> {
                cacheToBeUpdated.updateCacheViaAnnotation(updateCache, method.getParameterAnnotations(), id, v, args);
            });
        }
    }

    private void updateValuePool(Method invokedMethod, Map<?, ?> idObjectMap, boolean remove, boolean addIfAbsent) {
        if (idObjectMap == null) {
            return;
        }
        var vp = getValuePool(invokedMethod);

        if (remove) {
            var ids = idObjectMap.keySet();

            proxies.values().forEach(p -> {
                p.cache.removeById(ids); //will be removed from valuepool via the local caches, along with the ID from them.
            });
        } else {
            idObjectMap.forEach((id, v) -> {
                if (v != null) { //TODO: This should not be possible Verify it indeed isn't.
                    if (addIfAbsent) {
                        vp.put(id, v, true);
                        return;
                    }
                    vp.updateValueOrPutPreemptively(id, v);
                }
            });
        }
    }


    private String getCleanType(Method method) {

        var methodDescription = method.toGenericString(); //!!!!!!
        methodDescription = methodDescription.replace("private ", "").replace("protected ", "").replace("public ", ""); //terralol, TODO find some less cringy way to achieve this
        try {
            if (methodDescription.contains("<")) { //i.e. any collection (maps are not allowed)
                var firstSplit = methodDescription.split(" ");
                var secondSplit = firstSplit[0].split("<");
                var cleanType = secondSplit[secondSplit.length - 1].replace(">", "").replace("<", "").replace(",", "");
                return cleanType;
            } else {
                var split = methodDescription.split(" ");
                var cleanType = split[0].replace(" ", "");
                return cleanType;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MnemoProxy generateInternal(Method method, Object singletonBean) {
        var annotation = method.getAnnotation(Cached.class);

        if (annotation == null || method == null) {
            return null;
        }
        Class<?> returnedClassType = method.getReturnType();

        var handleCollectionKeysSeparately = annotation.allowSeparateHandlingForKeyCollections();
        var returnsCollection = Collection.class.isAssignableFrom(returnedClassType);

        var cacheParams = annotationValuesToCacheParameters(annotation, returnsCollection, handleCollectionKeysSeparately);
        generalControls(method, cacheParams);

        Class<? extends AbstractMnemosyneCache> algoClass = cacheParams.getCacheType();
        ValuePool valuePool = getOrCreateValuePool(method);
        AbstractMnemosyneCache cache = null;
        try {
            cache = algoClass.getDeclaredConstructor(CacheParameters.class, ValuePool.class).newInstance(cacheParams, valuePool);
        } catch (Exception e) {
            throw new MnemosyneRuntimeException(e);
        }
        var proxyService = new MnemoProxy<>(cache, method, singletonBean, valuePool, returnsCollection, handleCollectionKeysSeparately);

        proxies.put(method, proxyService);
        var proxyList = proxiesByValuePool.getOrDefault(valuePool, new ArrayList<>());
        proxyList.add(proxyService);
        proxiesByValuePool.put(valuePool, proxyList);
        cachesByName.put(annotation.cacheName(), proxyService);

        return proxyService;
    }

    private void generalControls(Method method, CacheParameters parameters) {
        var returnType = method.getReturnType();
        var arguments = method.getParameters();

        if (returnType.equals(Void.TYPE) && method.getAnnotation(Cached.class) != null) {
            throw new MnemosyneInitializationException("Void methods cannot be cached."); //TODO: Testfall
        }
        if ((method.getAnnotation(Cached.class) != null || method.getAnnotation(UpdatesCache.class) != null) && method.getAnnotation(UpdatesValuePool.class) != null) {
            throw new MnemosyneInitializationException("@UpdatesValuePool may only be used by methods that do not otherwise interact with mnemosyne");
        }
        if (cachesByName.get(parameters.getCacheName()) != null) {
            throw new MnemosyneInitializationException("Cache with the same name already exists!");
        }

        if (parameters.isHandleCollectionKeysSeparately()) {
            /*
             *  Find if any @Key annotations are present. If there are, use them. If not, use the arguments.
             */
            var possibleKeys = MnemoCommon.getParametersWithAnnotation(method, Key.class).keySet().stream().toList();
            if (possibleKeys.isEmpty()) {
                possibleKeys = Arrays.stream(arguments).toList();
            }
            if (possibleKeys.size() != 1) {
                throw new MnemosyneInitializationException("Separate key handling impossible for this number of arguments.");
            }

            if (Collection.class.isAssignableFrom(returnType)) {
                if (isUnacceptableSeparateHandlingTypes(returnType.getName())) {
                    throw new MnemosyneInitializationException("Separate key handling impossible for this return type.");
                }
                if (isUnacceptableSeparateHandlingTypes(possibleKeys.get(0).getType().getName())) {
                    throw new MnemosyneInitializationException("Separate key handling impossible for this key type.");
                }
            } else {
                throw new MnemosyneInitializationException("Separate key handling impossible for this return type.");
            }
        }
    }

    private void updateControls(Method method, UpdatesCache updateCache) {
        if (method != null && updateCache != null) {
            if (moreThanOneAnnotationsPresentInParameterList(method, UpdatedValue.class)) {
                throw new MnemosyneUpdateException("At most one UpdatedValue allowed");
            }

            if (updateCache.removeMode() == RemoveMode.NONE && updateCache.addMode() == AddMode.NONE) {
                throw new MnemosyneUpdateException("Invalid update mode: make sure you chose the proper RemoveMode or AddMode.");
            }

            if (updateCache.removeMode() != RemoveMode.NONE && updateCache.addMode() != AddMode.NONE) {
                var condAdd = updateCache.addOnCondition();
                var condRem = updateCache.removeOnCondition();
                if (condAdd.length == 1 && condAdd[0].isEmpty() && condRem.length == 1 && condRem[0].isEmpty()) {
                    throw new MnemosyneUpdateException("An UpdatesCache with no define conditions either adds or removes."); //TODO: test
                }
            }

            //TODO: Multiple updatesCache for the same target method is allowed, but what if they clash? See if it is better to check for controls, disable multiple updatesCache for the same  target method, or just print a warning.
        }
    }

    private boolean moreThanOneAnnotationsPresentInParameterList(Method method, Class<?> annotationType) {
        var paramannot = method.getParameterAnnotations();

        int i = 0;
        for (Annotation[] annotations : paramannot) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(annotationType)) {
                    ++i;
                }
            }
        }
        return i > 1;
    }


    private Object getAnnotatedUpdatedValue(Annotation[][] parameterAnnotations, Object[] args) {
        // var paramannot = method.getParameterAnnotations();

        int i = 0;
        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == UpdatedValue.class) {
                    return args[i];
                }
            }
            i += 1;
        }
        return null;
    }

    private boolean isUnacceptableSeparateHandlingTypes(String typename) {
        var isList = typename.equals("java.util.List");
        var isSet = typename.equals("java.util.Set");
        var isCollection = typename.equals("java.util.Collection");

        return (!isSet && !isList && !isCollection);
    }

}
