package com.gmalandrakis.mnemosyne.structures;

import java.util.Arrays;

public class CompoundId {

    Object[] idObjects;

    public CompoundId() {
        this.idObjects = idObjects;
    }
    public CompoundId(Object[] idObjects) {
        this.idObjects = idObjects;
    }


    public Object[] getIdObjects() {
        return idObjects;
    }

    public void setIdObjects(Object[] idObjects) {
        this.idObjects = idObjects;
    }

    public void addObject(Object[] idObjects) {
        this.idObjects = idObjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompoundId that)) return false;
        return Arrays.deepEquals(idObjects, that.idObjects);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(idObjects);
    }
}
