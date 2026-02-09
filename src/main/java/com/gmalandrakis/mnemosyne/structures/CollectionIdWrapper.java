package com.gmalandrakis.mnemosyne.structures;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class CollectionIdWrapper<ID> extends IdWrapper<ID> {
    Collection<ID> collection  = Collections.synchronizedSet(new HashSet<ID>());

    public CollectionIdWrapper(){
        //should only be used only in e.g. computeIfAbsent
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
    }
    public CollectionIdWrapper(Collection<ID> objs) {
        this.collection = Collections.synchronizedSet(new HashSet<ID>(objs));
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
    }

    public CollectionIdWrapper(Collection<ID> objs, int upperLimit) {
        this.collection = Collections.synchronizedSet(new HashSet<ID>(objs));
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
        this.upperHitLimit = upperLimit;
    }

    public void addAllToCollectionOrUpdate(Collection<ID> id) {
        collection.addAll(id);
    }

    public boolean addToCollectionOrUpdate(ID id) {
        return collection.add(id); //hits and timestamps are updated only when requesting the IDs.
    }

    public Collection<ID> getIds() {
        increaseHits();
        this.lastAccessed = System.currentTimeMillis();
        return collection;
    }

    /**
     * Gets the ID without updating access time and hits.
     * Should only be used when the ID is requested "internally", i.e. by mnemosyne itself or some cache eviction algorithm.
     * A use case is getting the IDs with an LRU-like or LFU policy: we would not want
     * to modify the access-time or the number of hits.
     */
    public Collection<ID> getIdsInternal(){
        return collection;
    }
}
