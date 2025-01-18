package com.gmalandrakis.mnemosyne.utils;

import com.gmalandrakis.mnemosyne.annotations.Id;
import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.annotations.UpdateKey;
import com.gmalandrakis.mnemosyne.annotations.UpdatedValue;
import com.gmalandrakis.mnemosyne.structures.CompoundId;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class GeneralUtils {

    public static Object deduceId(Object object) { //TODO: Use compoundId with the values of multiple @Id-annotated fields, or given id-names.
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
            var keyIdMap = new ConcurrentHashMap<>();

            ((Iterable) object).forEach(obj -> {
                keyIdMap.put(deduceId(obj), obj); //Returns a Map<ID, V>
            });
            return keyIdMap;
        }

        var idObjects = new ArrayList<>();
        try {
            var fields = Arrays.stream(object.getClass().getFields());
            var decfields = Arrays.stream(object.getClass().getDeclaredFields());
            var idField = Stream.concat(fields, decfields)
                    .filter(field -> field.getAnnotation(Id.class) != null).toList();
            if (!idField.isEmpty()) {
                idField.forEach(ids -> {
                    var idObject = tryGetObject(object, ids.getName());
                    idObjects.add(idObject);
                });
             /*   idField.forEach(idf -> {
                    idf.setAccessible(true);
                    try {
                        idObjects.add(idf.get(object));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });*/
            }
        } catch (Exception ee) {
            //todo
        }
        if (idObjects.isEmpty()) {
            try {
                var match = Arrays.stream(object.getClass().getDeclaredFields()).filter(field -> field.getName().equalsIgnoreCase("id")).toList();
                if (!match.isEmpty()) {
                    match.get(0).setAccessible(true);
                    return match.get(0).get(object);
                }
            } catch (Exception e) {
            }
        }

        return new CompoundId(idObjects.toArray());
    }


    @SuppressWarnings("unchecked")
    public static CompoundKey deduceCompoundKeyFromMethodAndArgs(Method method, Object[] args) {
        if (method == null || args == null || args.length == 0) {
            return new CompoundKey(new Object[0]);
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

    public static CompoundKey getCompoundKeyForUpdateNew(Map<String, Object> annotatedKeyNamesAndValues, String[] targetObjectKeys, Object targetObject) {
        List<Object> keyObjects = new ArrayList<>(); //

        annotatedKeyNamesAndValues.keySet().forEach(keyName -> {
            keyObjects.add(annotatedKeyNamesAndValues.get(keyName));
        });

        for (String keyName : targetObjectKeys) {

            keyObjects.add(tryGetObject(targetObject, keyName));
        }

        return new CompoundKey(keyObjects.toArray());
    }


    public static void sleepUninterrupted(long sleepTime) {
        boolean sleepComplete = false;
        long sleepStarted = 0;
        long remainingSleep;

        remainingSleep = sleepTime;
        while (!sleepComplete) {
            try {
                sleepStarted = System.currentTimeMillis();
                Thread.sleep(remainingSleep);
            } catch (InterruptedException e) {
                //oops!
            } finally {
                remainingSleep = remainingSleep - (System.currentTimeMillis() - sleepStarted);
            }
            if (remainingSleep <= 0) {
                sleepComplete = true;
            }
        }
    }

    public static boolean moreThanOneAnnotationsPresentInParameterList(Method method, Class<?> annotationType) {
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


    public static Object getAnnotatedUpdatedValue(Method method, Object[] args) {
        var paramannot = method.getParameterAnnotations();

        int i = 0;
        for (Annotation[] annotations : paramannot) {

            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(UpdatedValue.class)) {
                    return args[i];
                }
            }
            i += 1;
        }

        return null;
    }


    public static Map<String, Object> getUpdateKeyNamesAndCorrespondingValues(Method method, Object[] args) {
        var parameterAnnotations = method.getParameterAnnotations();
        var keyNameAndValue = new HashMap<String, Object>();
        int i = 0;

        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(UpdateKey.class)) {
                    keyNameAndValue.put(((UpdateKey) annotation).name(), args[i]);
                }
            }
            i += 1;
        }

        return keyNameAndValue;
    }


    public static Map<Parameter, Annotation> getParametersWithAnnotation(Method method, Class annotationType) {
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


    public static Object tryGetObject(Object targetObject, String keyName) {
        if (targetObject == null || keyName == null || keyName.isEmpty()) {
           throw new RuntimeException();
        }

        var targetClass = targetObject.getClass();
        var capitalizedKeyName = keyName.substring(0, 1).toUpperCase() + keyName.substring(1);

        try {
            var field = targetClass.getDeclaredField(keyName);
            field.setAccessible(true);
            return field.get(targetObject);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            String[] methodPrefixes = {"get", "is", "has"};
            for (String prefix : methodPrefixes) {
                try {
                    var method = targetClass.getMethod(prefix + capitalizedKeyName);
                    return method.invoke(targetObject);
                } catch (NoSuchMethodException ignored) {
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new RuntimeException("Failed to access method " + prefix + capitalizedKeyName, ex);
                }
            }
        }

        throw new RuntimeException("No field or accessible getter found for key: " + keyName + " in class: " + targetClass.getName());
    }


   /* public static Object tryGetObject(Object targetObject, String keyName) {
        Object toBereturned = null;
        var targetClass = targetObject.getClass();
        try {
            targetObject.getClass().getDeclaredField(keyName).trySetAccessible();
            return targetObject.getClass().getDeclaredField(keyName).get(targetObject);
        } catch (IllegalAccessException e) {
            var getter = keyName.substring(0, 1).toUpperCase() + keyName.substring(1);
            try {
                toBereturned = targetClass.getDeclaredMethod("get" + getter).invoke(targetObject);
            } catch (NoSuchMethodException | IllegalAccessException ee) {
                try {
                    toBereturned = targetClass.getDeclaredMethod("is" + getter).invoke(targetObject);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                    throw new RuntimeException("No field or getter could be invoked for field name" + keyName + " and type " + targetObject.getClass() + ". Make sure the class is public and/or the ID fields are either public or have a getter that follows Java naming conventions");
                }
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return toBereturned;
    }*/


}
