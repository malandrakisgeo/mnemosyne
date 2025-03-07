package com.gmalandrakis.mnemosyne.utils;

import com.gmalandrakis.mnemosyne.annotations.Id;
import com.gmalandrakis.mnemosyne.annotations.Key;
import com.gmalandrakis.mnemosyne.structures.CompoundId;
import com.gmalandrakis.mnemosyne.structures.CompoundKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneralUtils {

    public static Object deduceId(Object object) {
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


    @SuppressWarnings("unchecked")
    public static CompoundKey deduceCompoundKeyFromMethodAndArgs(Method method, Object[] args) {
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


    public static Object getFieldOrThrow(Object targetObject, String keyName) {
        if (targetObject == null || keyName == null || keyName.isEmpty()) {
            return null;
        }

        var targetClass = targetObject.getClass();
        var fieldValue = tryGetField(targetObject, keyName);
        if (fieldValue == null) {
            throw new RuntimeException("No field or accessible getter found for key: " + keyName + " in class: " + targetClass.getName());
        }

        return fieldValue;
    }

    public static Object tryGetField(Object targetObject, String keyName) {
        if (targetObject == null || keyName == null || keyName.isEmpty()) {
            return null;
        }

        var targetClass = targetObject.getClass();
        var capitalizedKeyName = keyName.substring(0, 1).toUpperCase() + keyName.substring(1);

        try {
            var field = targetClass.getDeclaredField(keyName);
            field.setAccessible(true);
            return field.get(targetObject);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            String[] prefixes = {"", "get", "is", "fetch", "has"};
            for (String prefix : prefixes) {
                try {
                    var method = targetClass.getMethod(prefix + capitalizedKeyName);
                    return method.invoke(targetObject);
                } catch (NoSuchMethodException | IllegalAccessException ignore) {
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException("Failed to access method " + prefix + capitalizedKeyName, ex);
                }
            }
        }
        return null;
    }


}
