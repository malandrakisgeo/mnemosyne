package com.gmalandrakis.mnemosyne.structures;

public abstract class IdWrapper<ID> {

    long lastAccessed; //will be increased only on explicit gets, not on updates.

    long createdOn;

    int hits; //will be increased only on explicit gets, not on updates.

    public void updateLastAccessed() {
        this.lastAccessed = System.currentTimeMillis();
    }
    public long getLastAccessed() {
        return lastAccessed;
    }

    public int getHits() {
        return hits;
    }

    public long getCreatedOn() {
        return createdOn;
    }
}
