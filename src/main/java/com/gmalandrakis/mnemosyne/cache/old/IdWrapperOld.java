package com.gmalandrakis.mnemosyne.cache.old;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * A key either corresponds to a single object, or to a collection of objects (e.g. when a method returns a list or not).
 * This is a wrapper class for the corresponding IDs.
 * @param <ID>
 */
public class IdWrapperOld<ID> {  //Id of object, and not the object itself!
    long lastAccessed;

    long createdOn;

    int hits; //will be increased only on explicit gets, not on updates.

    ID singleObject; //Id of object, and not the object itself. Null if a cache fetches collections.

    Collection<ID> idCollection; //Ids of objects, and not the objects themselves!

    public IdWrapperOld(ID singleObject) {
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = this.createdOn;
        this.singleObject = singleObject;
        hits = 0;
    }

    public IdWrapperOld(Collection<ID> objs) {
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = this.createdOn;
        this.idCollection = new HashSet<>(objs);
        hits = 0;
    }

    public ID getId() {
        hits += 1;
        updateLastAccessed();

        return singleObject;
    }

    public void setsingleObject(ID id) {
        this.singleObject = id;
    }

    public Collection<ID> getIds() {
        hits += 1;
        updateLastAccessed();

        if (singleObject != null) {
            return Collections.singletonList(singleObject);
        }
        return this.idCollection;
    }

    public void setCollection(Collection<ID> collection) {
        idCollection = collection;
    }

    public void addToCollectionOrUpdate(ID id) {
        updateLastAccessed();

        if (idCollection == null) {
            idCollection = new HashSet<ID>();
        }
        idCollection.add(id);
    }

    public void updateLastAccessed() {
        this.lastAccessed = System.currentTimeMillis();
    }

    public void addAllToCollectionOrUpdate(Collection<ID> id) {
        updateLastAccessed();

        if (idCollection == null) {
            idCollection = new HashSet<ID>();
        }
        idCollection.addAll(id);
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
