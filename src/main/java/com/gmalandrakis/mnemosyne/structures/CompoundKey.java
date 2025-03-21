package com.gmalandrakis.mnemosyne.structures;

import java.util.Arrays;

/**
 * Unless an argument annotated with {@link com.gmalandrakis.mnemosyne.annotations.Key @Key} is present,
 * all the arguments to a {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached} function are assembled to a CompoundKey.
 * <p>
 * The CompoundKey consists of an array of objects. The deep contents of the objects are taken into account for the hash code and the equality.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
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
