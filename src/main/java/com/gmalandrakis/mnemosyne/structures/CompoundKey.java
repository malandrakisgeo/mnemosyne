package com.gmalandrakis.mnemosyne.structures;

import java.util.Arrays;

/**
 * Unless an argument annotated with {@link com.gmalandrakis.mnemosyne.annotations.Key @Key} is present,
 * all the arguments to a {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached} function are assembled to a CompoundKey.
 * <p>
 * The CompoundKey consists of an array of arguments and deduces the hash of the Key
 * with a recursive algorithm that takes into account all the fields of all the arguments, and all subclasses they include.
 * <p>
 * If a CompoundKey exists, it is used as type in the implementation of {@link com.gmalandrakis.mnemosyne.core.AbstractCacheValue AbstractCacheValue} .
 * </p>
 */
public class CompoundKey {

    Object[] keyObjects;

    public CompoundKey(Object[] keyObjects) {
        this.keyObjects = keyObjects;
    }


    public Object[] getKeyObjects() {
        return keyObjects;
    }

    public void setKeyObjects(Object[] keyObjects) {
        this.keyObjects = keyObjects;
    }

    public void addObject(Object[] keyObjects) {
        this.keyObjects = keyObjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompoundKey that)) return false;
        return Arrays.deepEquals(keyObjects, that.keyObjects);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(keyObjects);
    }
}
