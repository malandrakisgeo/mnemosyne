package com.gmalandrakis.mnemosyne.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map;

public class GeneralUtils {
    private static final Map<Class<?>, Class<?>> primitiveWrapperMap =
            Map.of(boolean.class, Boolean.class,
                    byte.class, Byte.class,
                    char.class, Character.class,
                    double.class, Double.class,
                    float.class, Float.class,
                    int.class, Integer.class,
                    long.class, Long.class,
                    short.class, Short.class); //special thanks to logicbig for this structure

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
            String[] prefixes = {"", "get", "is", "fetch", "has", "find", "findBy"};
            for (String prefix : prefixes) {
                try {
                    var method = targetClass.getMethod(prefix + capitalizedKeyName);
                    method.setAccessible(true);
                    return method.invoke(targetObject);
                } catch (NoSuchMethodException | IllegalAccessException ignore) {
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException("Failed to access method " + prefix + capitalizedKeyName, ex);
                }
            }
        }
        return null;
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

    public static boolean allNull(Collection<?> col) {
        boolean allNull = true;
        for (Object o : col) {
            if (o != null) {
                allNull = false;
                break;
            }
        }
        return allNull;
    }

    public static boolean isAssignableTo(Class<?> from, Class<?> to) { //stolen from logicbig.com!
        if (to.isAssignableFrom(from)) {
            return true;
        }
        if (from.isPrimitive()) {
            return primitiveWrapperMap.get(from) == to;
        }
        if (to.isPrimitive()) {
            return primitiveWrapperMap.get(to) == from;
        }
        return false;
    }
}
