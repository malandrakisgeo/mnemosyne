package com.gmalandrakis.mnemosyne.structures;

public class CacheValue<T> {
    private int cachesUsingValue;
    private T value;

    public CacheValue(T t) {
        this.value = t;
        cachesUsingValue = 1; //manually increased or decreased afterwards by the ValuePool
    }

    public synchronized int increaseNumberOfUses() {
        return ++cachesUsingValue;
    }

    public synchronized int decreaseNumberOfUses() {
        return --cachesUsingValue;
    }

    public T getValue() { //TODO: Make it return deep copies if a type is clonable
        return value;
    }

    public int getNumberOfUses() {
        return cachesUsingValue;
    }

    public synchronized void updateValue(T t) {
        this.value = t;
    }
}
