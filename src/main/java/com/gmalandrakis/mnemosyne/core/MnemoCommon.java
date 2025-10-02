package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Id;
import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.annotations.UpdatedValue;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRuntimeException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneUpdateException;
import com.gmalandrakis.mnemosyne.structures.CompoundId;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.gmalandrakis.mnemosyne.utils.GeneralUtils.tryGetField;

/**
 * Static mnemosyne-specific functions used by both MnemoProxy and MnemoService.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class MnemoCommon {
    /*
        The reason this is not a part of the /utils package, is that we usually place under /utils functions
        that could be used more or less anywhere, i.e. functions that are not directly related to the logic
        of the project, and for mostly trivial tasks. The functions of this class
        *are* directly related to the logic of mnemosyne (actually so intermingled with it that they would be useless anywhere else),
        and some of them are not very trivial.
     */

    //Returns just the ID of an object for single-value caches, or the id-value map for collection caches. TODO: Break in two separate functions for these purposes
    public static Object deduceIdOrMap(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof Number) {
            return object;
        }

        if (object instanceof CharSequence) {
            return ((CharSequence) object).toString();
        }

        if (object instanceof Iterable<?>) {
            var idObjectMap = new ConcurrentHashMap<>();

            ((Iterable) object).forEach(obj -> {
                idObjectMap.put(deduceIdOrMap(obj), obj); //Returns a Map<ID, V>
            });
            return idObjectMap;
        }

        var idObjects = new ArrayList<>();
        var fields = Arrays.stream(object.getClass().getFields());
        var decfields = Arrays.stream(object.getClass().getDeclaredFields());
        var idField = Stream.concat(fields, decfields)
                .filter(field -> field.getAnnotation(Id.class) != null).toList();
        idField.forEach(ids -> {
            var idObject = getFieldOrThrow(object, ids.getName());
            idObjects.add(idObject);
        });
        if (idObjects.isEmpty()) {
            return tryGetIDField(object);
        }

        return new CompoundId(idObjects.toArray());
    }

    static Object getAnnotatedUpdatedValue(Annotation[][] parameterAnnotations, Object[] args) {
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

    static String updateTypeInAnnotated(Annotation[][] parameterAnnotations,
                                        Type[] types) {
        int i = 0;
        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == UpdatedValue.class) {
                    var name = types[i].getTypeName();
                    if (!name.contains(">")) {
                        return name;
                    } else {
                        var secondSplit = name.split("<");
                        var cleanType = secondSplit[secondSplit.length - 1].replace(">", "").replace("<", "").replace(",", "");
                        return cleanType;
                    }
                }
            }
            i += 1;
        }
        return null;
    }

    static String updateType(Method method) {
        var type = MnemoCommon.updateTypeInAnnotated(method.getParameterAnnotations(), method.getGenericParameterTypes());
        if (type == null) {
            var possibleType = method.getGenericReturnType().getTypeName();

            if (!possibleType.contains(">")) {
                type = possibleType;
            } else {
                var secondSplit = possibleType.split("<");
                var cleanType = secondSplit[secondSplit.length - 1].replace(">", "").replace("<", "").replace(",", "");
                return cleanType;
            }
        }
        return type;
    }


    @SuppressWarnings("unchecked")
    static CompoundKey deduceCompoundKeyFromMethodAndArgs(Method method, Object[] args) {
        //TODO: Simplify so that whenever a method uses just a single argument of type Number, charSequence, or UUID as a key, no compoundKey is used.
        if (args == null || args.length == 0) {
            return new CompoundKey(new Object[0]); //handling for methods with no keys (e.g. returning the same list everytime)
        }
        var parameterAnnotations = method.getParameterAnnotations();
        var keys = new ArrayList<Object>();
        int i = 0;
        outerloop:
        for (Annotation[] annotations : parameterAnnotations) {

            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(Key.class)) {
                    keys.add(args[i]);
                }
            }
            i += 1;
        }

        return keys.isEmpty() ? new CompoundKey(args) : new CompoundKey(keys.toArray());
    }


    static Map<Parameter, Annotation> getParametersWithAnnotation(Method method, Class annotationType) {
        var paramList = new HashMap<Parameter, Annotation>();
        var parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            var annotation = parameter.getAnnotation(annotationType);
            if (annotation != null) {
                paramList.put(parameter, annotation);
            }
        }

        return paramList;
    }

    static Class<?>[] getAnnotatedParamTypes(Method method, Class annotationType) {
        Class<?>[] paramList = {};
        var paramArray = new ArrayList<Class<?>>();
        var parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            var annotation = parameter.getAnnotation(annotationType);
            if (annotation != null) {
                paramArray.add(parameter.getType());
            }
        }

        return paramArray.toArray(paramList);
    }

    static Object tryGetIDField(Object targetObject) { //This is a final resort in case there are no fields annotated as @Id. TODO: improve
        try {
            return getFieldOrThrow(targetObject, "Id");
        } catch (Exception e) {
            try {
                return getFieldOrThrow(targetObject, "ID");
            } catch (Exception ee) {
                return getFieldOrThrow(targetObject, "id");
            }
        }
    }


    static Object getFieldOrThrow(Object targetObject, String keyName) {
        if (targetObject == null || keyName == null || keyName.isEmpty()) {
            return null;
        }

        var targetClass = targetObject.getClass();
        var fieldValue = tryGetField(targetObject, keyName);
        if (fieldValue == null) {
            throw new MnemosyneRuntimeException("No field or accessible getter found for key: " + keyName + " in class: " + targetClass.getName());
        }

        return fieldValue;
    }

    static LinkedHashMap<String, Object> linkTargetObjectKeysToObjects(List<String> targetObjectKeys, Object targetObject) {
        var map = new LinkedHashMap<String, Object>(); //keeps the order intact
        if (targetObject != null) {
            for (String keyName : targetObjectKeys) {
                if (!keyName.isEmpty()) {
                    map.put(keyName, MnemoCommon.getFieldOrThrow(targetObject, keyName));
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
    static CompoundKey getCompoundKeyForUpdate(LinkedHashMap<String, Object> annotatedKeyNamesAndValues, LinkedHashMap<String, Object> targetKeyNamesAndValues,
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

    static Boolean getCondition(String[] conditions, Map<String, Object> annotatedKeyNamesAndValues, Object updatedObject, boolean conditionalAND) {
        if (conditions == null || (conditions.length == 1 && conditions[0].isEmpty())) {
            return true; //If there is no condition, we tell mnemosyne "just add or remove the value without caring about it's fields"
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
                var updated = tryGetField(updatedObject, fieldName);
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

}
