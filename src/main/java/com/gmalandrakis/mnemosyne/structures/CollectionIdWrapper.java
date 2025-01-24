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

    public void addAllToCollectionOrUpdate(Collection<ID> id) {
        collection.addAll(id);
    }

    public void addToCollectionOrUpdate(ID id) {
        collection.add(id); //hits and timestamps are updated only when requesting the IDs.
    }

    public Collection<ID> getIds() {
        hits += 1;
        updateLastAccessed();
        return collection;
    }
}
