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
import java.util.stream.Collectors;

import static com.gmalandrakis.mnemosyne.utils.GeneralUtils.getAnnotatedUpdatedValue;
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
        var id = GeneralUtils.deduceId(object);
        updateValuePool(method, id, object, false, false, args); //TODO: Fetch the actual values
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
        var id = GeneralUtils.deduceId(object);
        updateValuePool(method,id, object, false, false, args);
        updateRelatedCaches(method, id, object, args);

        return object;
    }

    public Object fetchFromCacheOrInvokeMethodAndUpdate(Method method, Object... args) {
        var cacheProxy = proxies.get(method);
        assert (cacheProxy != null);
        Object result = tryFetchFromCache(cacheProxy, args);
        if (result == null) {
            var idValMap  = cacheProxy.getFromUnderlyingMethodAndUpdateMainCache(args);
            threadPool.execute(()->{
                idValMap.forEach((id, v)->{
                    updateRelatedCaches(method, id ,v, args);
                });
            });
            result = cacheProxy.deduce(idValMap);
        }
        return result;
    }


    public void updateRelatedCaches(Method method, Object id, Object possibleUpdatedValue, Object... args) {
        var updatesCaches = method.getAnnotation(UpdatesCaches.class);
        if (updatesCaches != null) {
            for (UpdatesCache updateCache : updatesCaches.value()) {
                this.updateRelatedCache(updateCache, method.getParameterAnnotations(),id, possibleUpdatedValue, args);
            }
        }
        var updateCache = method.getAnnotation(UpdatesCache.class);
        if (updateCache != null) {
            this.updateRelatedCache(updateCache, method.getParameterAnnotations(),id, possibleUpdatedValue, args);
        }
    }

    //TODO: Simplify the flow here
    private void updateRelatedCache(UpdatesCache updateCache, Annotation[][] methodParameterAnnotations, Object idOfUpdatedValue, Object updatedValue, Object... args) {
        var targetObjectKeyNamesAndValues = updateCache.targetObjectKeys();
        var cacheToBeUpdated = this.cachesByName.get(updateCache.name());

        assert (cacheToBeUpdated != null);
        var cachedMethod = cacheToBeUpdated.getCachedMethod();
        var annotatedKeyNamesAndValues = getUpdateKeyNamesAndCorrespondingValues(methodParameterAnnotations, updateCache.annotatedKeys(), args);


        //final Object finalUpdatedValue = updatedValue;


        var targetKeyNamesAndValues = linkTargetObjectKeysToObjects(List.of(targetObjectKeyNamesAndValues), updatedValue);

        var conditionalRemoval = getCondition(updateCache.removeOnCondition(), annotatedKeyNamesAndValues, updatedValue, updateCache.conditionalANDGate());
        var conditionalAdd = getCondition(updateCache.addOnCondition(), annotatedKeyNamesAndValues, updatedValue, updateCache.conditionalANDGate());
        var key = getCompoundKeyForUpdate(annotatedKeyNamesAndValues, targetKeyNamesAndValues, updateCache.keyOrder(), cachedMethod, cacheToBeUpdated.isSpecialCollectionHandlingEnabled());

        if (idOfUpdatedValue == null && updatedValue == null) { //this can only happen on key removal.
            cacheToBeUpdated.updateByRemoving(key, null, conditionalRemoval, updateCache.removeMode());
            return;
        }
        if (idOfUpdatedValue instanceof Map) { //This should be impossible with this flow. Test and verify
            cacheToBeUpdated.updateByRemoving(key, (Map) idOfUpdatedValue, conditionalRemoval, updateCache.removeMode());
            cacheToBeUpdated.updateByAdding(key, (Map) idOfUpdatedValue, conditionalAdd, updateCache.addMode());

        } else {
            cacheToBeUpdated.updateByRemoving(key, Map.of(idOfUpdatedValue, updatedValue), conditionalRemoval, updateCache.removeMode());
            cacheToBeUpdated.updateByAdding(key, Map.of(idOfUpdatedValue, updatedValue), conditionalAdd, updateCache.addMode());
        }
    }

    private void updateValuePool(Method invokedMethod, Object idOfUpdatedValue, Object updatedValue, boolean remove, boolean addIfAbsent, Object... args) {
        if(idOfUpdatedValue == null){
            return;
        }
        var vp = getValuePool(invokedMethod);
        if (remove) {
            proxies.values().forEach(p -> p.cache.removeById(idOfUpdatedValue)); //will be removed from valuepool via the local caches, along with the ID from them.
            return;
        } else {
            if (addIfAbsent) {
                vp.put(idOfUpdatedValue, updatedValue, true);
                return;
            }
            vp.updateValueOrPutPreemptively(idOfUpdatedValue, updatedValue);
        }

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

    ValuePool getValuePool(Method method) {
        var cleanType = getCleanType(method);
        return valuePoolConcurrentHashMap.computeIfAbsent(cleanType, k -> new ValuePool<>());
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

    private Boolean getCondition(String[] conditions, Map<String, Object> annotatedKeyNamesAndValues, Object updatedObject, boolean conditionalAND) {
        if (conditions == null || (conditions.length == 1 && conditions[0].isEmpty())) {
            return null;
        }

        List<Boolean> booleans = new ArrayList<>();
        for (String possibleFieldName : conditions) {
            String fieldName = possibleFieldName;
            boolean negated = possibleFieldName.startsWith("!");
            if (negated) {
                fieldName = fieldName.substring(1);
            }
            var annotatedObject = annotatedKeyNamesAndValues.get(fieldName);
          /*  if (targetObject != null && annotatedObject != null) {
                if (boolean.class.isAssignableFrom(annotatedObject.getClass()) && boolean.class.isAssignableFrom(targetObject.getClass())) {
                    throw new RuntimeException("Condition error: two boolean conditions with the same name");
                }
            }*/
            if (annotatedObject != null && Boolean.class.isAssignableFrom(annotatedObject.getClass())) {
                booleans.add(negated != (Boolean) annotatedObject);
            }

            if (updatedObject != null) {
                var updated = GeneralUtils.tryGetField(updatedObject, fieldName);
                if (updated != null && Boolean.class.isAssignableFrom(updated.getClass())) {
                    booleans.add(negated != (Boolean) updated);
                }
            }
        }
        if (conditionalAND) {
            return !booleans.isEmpty() && !booleans.contains(Boolean.FALSE);
        } else {
            return !booleans.isEmpty() && booleans.contains(Boolean.TRUE); //conditional OR
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
        ValuePool valuePool = getValuePool(method);
        AbstractMnemosyneCache cache = null;
        try {
            cache = algoClass.getDeclaredConstructor(CacheParameters.class, ValuePool.class).newInstance(cacheParams, valuePool);
        } catch (Exception e) {
            throw new MnemosyneRuntimeException(e);
        }
        var proxyService = new MnemoProxy<>(cache, method, singletonBean, valuePool, returnsCollection, handleCollectionKeysSeparately);

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

    private void updateControls(Method method, UpdatesCache updateCache) {
        if (method != null && updateCache != null) {
            if (moreThanOneAnnotationsPresentInParameterList(method, UpdatedValue.class)) {
                throw new MnemosyneUpdateException("At most one UpdatedValue allowed");
            }

            if (updateCache.removeMode() == UpdatesCache.RemoveMode.NONE && updateCache.addMode() == UpdatesCache.AddMode.NONE) {
                throw new MnemosyneUpdateException("Invalid update mode: make sure you chose the proper RemoveMode or AddMode.");
            }

            if (updateCache.removeMode() != UpdatesCache.RemoveMode.NONE && updateCache.addMode() != UpdatesCache.AddMode.NONE) {
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

    private LinkedHashMap<String, Object> linkTargetObjectKeysToObjects(List<String> targetObjectKeys, Object targetObject) {
        var map = new LinkedHashMap<String, Object>(); //keeps the order intact
        if (targetObject != null) {
            for (String keyName : targetObjectKeys) {
                if (!keyName.isEmpty()) {
                    map.put(keyName, GeneralUtils.getFieldOrThrow(targetObject, keyName));
                }
            }
        }
        return map;
    }

    /*
        testfall:
        1. mia methodos pairnei ena set ws argument.
        2. Mia allh methodos kanei @UpdatesValue th prwth.
        3. Doulevei.
     */
    private CompoundKey getCompoundKeyForUpdate(LinkedHashMap<String, Object> annotatedKeyNamesAndValues, LinkedHashMap<String, Object> targetKeyNamesAndValues,
                                                String[] names, Method cachedMethod, boolean specialHandling) {
        List<Object> keyObjects = new ArrayList<>();
        if (annotatedKeyNamesAndValues != null && targetKeyNamesAndValues != null && !annotatedKeyNamesAndValues.isEmpty() && !targetKeyNamesAndValues.isEmpty()) {
            for (String name : names) {
                var annotatedValue = annotatedKeyNamesAndValues.get(name);
                var targetValue = targetKeyNamesAndValues.get(name);
                if (targetValue != null && annotatedValue != null) {
                    throw new MnemosyneUpdateException("Two possible keys with the same name found");
                }
                if (targetValue != null) {
                    keyObjects.add(targetValue);
                } else {
                    keyObjects.add(annotatedValue);
                }
            }
        } else {
            //TODO: Delete this disgrace and replace with something less crappy, that handles implementations of Set and List too.
            boolean keyIsASet = false;
            boolean keyIsAList = false;
            if (cachedMethod.getParameters().length == 1) {
                keyIsASet = Set.class.isAssignableFrom(cachedMethod.getParameters()[0].getType()) && !specialHandling; //special collection handling internally uses only the values each by each, without wrapping them as Sets or Lists.
                keyIsAList = List.class.isAssignableFrom(cachedMethod.getParameters()[0].getType()) && !specialHandling;
            }
            /*
                The reason for the code below is that a CompoundKey that contains an object A is different from a compoundKey containing a List or a Set with an object A.
                If a cached function takes a list as an argument and the value A is cached in a list, a CompoundKey(List(A)) is needed. CompoundKey(A) will not yield a result.
             */
            Set hashSet = new HashSet();
            List arrayList = new ArrayList();

            var linkedHashMaps = new LinkedHashMap[]{annotatedKeyNamesAndValues, targetKeyNamesAndValues};
            if (keyIsASet) {
                for (LinkedHashMap linkedHashMap : linkedHashMaps) {
                    if (linkedHashMap != null) {
                        linkedHashMap.keySet().forEach(keyName -> {
                            hashSet.add(linkedHashMap.get(keyName));
                        });
                    }
                }
                keyObjects.add(hashSet);
            } else if (keyIsAList) {
                for (LinkedHashMap linkedHashMap : linkedHashMaps) {
                    if (linkedHashMap != null) {
                        linkedHashMap.keySet().forEach(keyName -> {
                            arrayList.add(linkedHashMap.get(keyName));
                        });
                    }
                }
                keyObjects.add(arrayList);
            } else {
                for (LinkedHashMap linkedHashMap : linkedHashMaps) {
                    if (linkedHashMap != null) {
                        linkedHashMap.keySet().forEach(keyName -> {
                            keyObjects.add(linkedHashMap.get(keyName));
                        });
                    }
                }
            }
        }

        return new CompoundKey(keyObjects.toArray());
    }

    private LinkedHashMap<String, Object> getUpdateKeyNamesAndCorrespondingValues(Annotation[][] parameterAnnotations, String[] keyNames, Object[] args) {
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

    private boolean isUnacceptableSeparateHandlingTypes(String typename) {
        var isList = typename.equals("java.util.List");
        var isSet = typename.equals("java.util.Set");
        var isCollection = typename.equals("java.util.Collection");

        return (!isSet && !isList && !isCollection);
    }

}
