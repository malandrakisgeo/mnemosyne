package com.gmalandrakis.mnemosyne.utils;

import java.util.Collection;
import java.util.Map;

public class GenericUtils {
    private static final Map<Class<?>, Class<?>> primitiveWrapperMap =
            Map.of(boolean.class, Boolean.class,
                    byte.class, Byte.class,
                    char.class, Character.class,
                    double.class, Double.class,
                    float.class, Float.class,
                    int.class, Integer.class,
                    long.class, Long.class,
                    short.class, Short.class); //special thanks to logicbig for this structure

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
