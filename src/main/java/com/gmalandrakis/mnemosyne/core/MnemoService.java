package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.*;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.exception.MnemosyneInitializationException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRuntimeException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneUpdateException;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.gmalandrakis.mnemosyne.utils.ParameterUtils.annotationValuesToCacheParameters;

/**
 * A service class used by {@link com.gmalandrakis.mnemosyne.spring.SpringInterceptor SpringInterceptor}.
 * It generates {@link MnemoProxy proxy objects} for all the intercepted methods and stores them in a HashMap.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class MnemoService {

    private final ConcurrentHashMap<String, ValuePool> valuePoolConcurrentHashMap = new ConcurrentHashMap<>(); //pools by fully qualified object name
    private final ConcurrentHashMap<Method, MnemoProxy> proxies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MnemoProxy> cachesByName = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public Object invokeMethodAndUpdate(Method method, Object obj, Object... args) {
        Object object = null;
        try {
            object = method.invoke(obj, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        Object finalObject = object;
        // threadPool.execute(() -> {
        updateRelatedCaches(method, finalObject, args); //TODO: Decide whether this should run in threadPool.execute to avoid delays
        //}); //we don't want the updates to delay returning
        return object;
    }

    public Object fetchFromCacheOrInvokeMethodAndUpdate(Method method, Object obj, Object... args) {
        var cacheProxy = proxies.get(method);
        assert (cacheProxy != null);
        Object result = tryFetchFromCache(cacheProxy, args);
        if (result == null) {
            result = cacheProxy.getFromUnderlyingMethodAndUpdateMainCache(obj, args);
            final var threadResult = result;
            //threadPool.execute(() -> { //We want to update all related caches, without delaying returning
            updateRelatedCaches(method, threadResult, args); // //TODO: Decide whether this should run in threadPool.execute to avoid delays
            // });
        }
        return result;
    }

    public void updateRelatedCaches(Method method, Object possibleUpdatedValue, Object... args) {
        var updatesCaches = method.getAnnotation(UpdatesCaches.class);

        if (updatesCaches != null) {
            for (UpdateCache updateCache : updatesCaches.value()) {
                this.updateRelatedCache(updateCache, method, possibleUpdatedValue, args);
            }
        }
        var updateCache = method.getAnnotation(UpdateCache.class);
        if (updateCache != null) {
            this.updateRelatedCache(updateCache, method, possibleUpdatedValue, args);
        }
    }

    private void updateRelatedCache(UpdateCache updateCache, Method method, Object possibleUpdatedValue, Object... args) {
        Object updatedValue = getAnnotatedUpdatedValue(method, args); // check if any of the args is annotated as @UpdatedValue.
        if (updatedValue == null && possibleUpdatedValue == null) {//If neither an @UpdatedValue is present, nor an updatedCacheValue was given, there is nothing to do.
            return;
        }

        if (updatedValue == null) {
            updatedValue = possibleUpdatedValue; // Which means that if there is any @UpdatedValue in the arguments, the result of the method is not used for updates!. TODO: Write documentation
        }

        var annotatedKeyNamesAndValues = getUpdateKeyNamesAndCorrespondingValues(method, args);
        var targetObjectKeyNamesAndValues = updateCache.targetObjectKeys();
        var idOfUpdatedValue = GeneralUtils.deduceId(updatedValue);
        final Object finalUpdatedValue = updatedValue;
        var cacheToBeUpdated = this.cachesByName.get(updateCache.name());

        assert (cacheToBeUpdated != null);

        var key = getCompoundKeyForUpdateNew(annotatedKeyNamesAndValues, List.of(targetObjectKeyNamesAndValues), finalUpdatedValue);

        if (idOfUpdatedValue instanceof Map) {
            cacheToBeUpdated.updateCacheKeysAndIds(key, (Map) idOfUpdatedValue, updateCache);
        } else {
            cacheToBeUpdated.updateCacheKeysAndIds(key, Map.of(idOfUpdatedValue, updatedValue), updateCache);
        }

    }

    public List<MnemoProxy> generateCachesForClass(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(Cached.class) != null)
                .map(this::generateForMethod)
                .toList();
    }

    public void generateUpdatesForClass(Class<?> clazz) {
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(UpdateCache.class) != null)
                .forEach(this::generateUpdaterForMethod);
    }

    MnemoProxy generateForMethod(Method method) {
        var annotation = method.getAnnotation(Cached.class);
        if (annotation != null) {
            return generateInternal(method, annotation);
        }
        return null;
    }

    void generateUpdaterForMethod(Method method) {
        var annotation = method.getAnnotation(UpdateCache.class);
        if (annotation != null) {
            updateControls(method, annotation);
        }
    }

    Object tryFetchFromCache(MnemoProxy cacheProxy, Object... args) { //Used for testability
        assert (cacheProxy != null);
        return cacheProxy.getFromCache(args);
    }

    ValuePool createValuePool(Method method) {
        var genericString = method.toGenericString(); //!!!!!!
        genericString = genericString.replace("private ", "").replace("protected ", "").replace("public ", ""); //terralol, find some less cringy way to achieve this

        try {
            if (genericString.contains("<")) { //i.e. any collection (maps are not allowed)
                var firstSplit = genericString.split(" ");
                var secondSplit = firstSplit[0].split("<");
                var cleanType = secondSplit[secondSplit.length - 1].replace(">", "").replace("<", "").replace(",", "");
                return valuePoolConcurrentHashMap.computeIfAbsent(cleanType, k -> new ValuePool<>());

            } else {
                var split = genericString.split(" ");
                var cleanType = split[0].replace(" ", "");
                return valuePoolConcurrentHashMap.computeIfAbsent(cleanType, k -> new ValuePool<>());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private MnemoProxy generateInternal(Method method, Cached annotation) {
        if (annotation == null || method == null) {
            return null;
        }
        Class<?> returnedClassType = method.getReturnType();

        var handleCollectionKeysSeparately = annotation.allowSeparateHandlingForKeyCollections();
        var returnsCollection = Collection.class.isAssignableFrom(returnedClassType);

        var cacheParams = annotationValuesToCacheParameters(annotation, returnsCollection, handleCollectionKeysSeparately);
        generalControls(method, cacheParams);

        Class<? extends AbstractMnemosyneCache> algoClass = cacheParams.getCacheType();
        ValuePool valuePool = createValuePool(method);
        AbstractMnemosyneCache cache = null;
        try {
            cache = algoClass.getDeclaredConstructor(CacheParameters.class, ValuePool.class).newInstance(cacheParams, valuePool);
        } catch (Exception e) {
            throw new MnemosyneRuntimeException(e);
        }
        var proxyService = new MnemoProxy<>(cache, method, valuePool, returnsCollection, handleCollectionKeysSeparately);

        proxies.put(method, proxyService);
        cachesByName.put(annotation.cacheName(), proxyService);

        return proxyService;
    }

    private void generalControls(Method method, CacheParameters parameters) {
        var returnType = method.getReturnType();
        var arguments = method.getParameters();

        if (returnType.equals(Void.TYPE) && method.getAnnotation(Cached.class) != null) {
            throw new MnemosyneInitializationException("Void methods cannot be cached."); //TODO: Testfall
        }

        if (cachesByName.get(parameters.getCacheName()) != null) {
            throw new MnemosyneInitializationException("Cache with the same name already exists!");
        }

        if (parameters.isHandleCollectionKeysSeparately()) {
            /*
             *  Find if any @Key annotations are present. If there are, use them. If not, use the arguments.
             */
            var possibleKeys = GeneralUtils.getParametersWithAnnotation(method, Key.class).keySet().stream().toList();
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

    private void updateControls(Method method, UpdateCache updateCache) {
        if (method != null && updateCache != null) {
           /* if (updateCache.addIfAbsent() &&
                    (updateCache.removesValueFromAllCollections() || updateCache.remove() || updateCache.removesValueFromSingleCollection() || updateCache.invalidatesCache())) {
                throw new MnemosyneUpdateException("Wrong flags: you cannot both add and remove a value.");
            }*/
            if (moreThanOneAnnotationsPresentInParameterList(method, UpdatedValue.class)) {
                throw new MnemosyneUpdateException("At most one UpdatedValue allowed");
            }
            //TODO: Exception when two @UpdateCache check the same cache
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


    private Object getAnnotatedUpdatedValue(Method method, Object[] args) {
        var paramannot = method.getParameterAnnotations();

        int i = 0;
        for (Annotation[] annotations : paramannot) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == UpdatedValue.class) {
                    return args[i];
                }
            }
            i += 1;
        }

        return null;
    }

    private CompoundKey getCompoundKeyForUpdateNew(Map<String, Object> annotatedKeyNamesAndValues, List<String> targetObjectKeys, Object targetObject) {
        List<Object> keyObjects = new ArrayList<>(); //

        annotatedKeyNamesAndValues.keySet().forEach(keyName -> {
            keyObjects.add(annotatedKeyNamesAndValues.get(keyName));
        });

        for (String keyName : targetObjectKeys) {
            keyObjects.add(GeneralUtils.tryGetObject(targetObject, keyName));
        }

        return new CompoundKey(keyObjects.toArray());
    }


    private Map<String, Object> getUpdateKeyNamesAndCorrespondingValues(Method method, Object[] args) {
        var parameterAnnotations = method.getParameterAnnotations();
        var keyNameAndValue = new HashMap<String, Object>();
        int i = 0;

        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == UpdateKey.class) {
                    keyNameAndValue.put(((UpdateKey) annotation).name(), args[i]);
                }
            }
            i += 1;
        }

        return keyNameAndValue;
    }


    private boolean isUnacceptableSeparateHandlingTypes(String typename) {
        var isList = typename.equals("java.util.List");
        var isSet = typename.equals("java.util.Set");
        var isCollection = typename.equals("java.util.Collection");

        return (!isSet && !isList && !isCollection);
    }

}
