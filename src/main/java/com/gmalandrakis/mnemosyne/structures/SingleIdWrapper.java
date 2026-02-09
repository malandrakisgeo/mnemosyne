package com.gmalandrakis.mnemosyne.structures;

public class SingleIdWrapper<ID> extends IdWrapper<ID> {
    ID singleId;

    public SingleIdWrapper(ID singleId){
        this.singleId = singleId;
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
    }
    public SingleIdWrapper(ID singleId, int upperLimit){
        this.singleId = singleId;
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
        this.upperHitLimit = upperLimit;
    }

    /**
     * Gets the ID, and updates access time and hits.
     * Unless these fields are irrelevant for the eviction algorithm,
     * this should only be used when the ID is requested  "externally" (e.g. by a user)
     */
    public ID getId() {
        increaseHits();
        this.lastAccessed = System.currentTimeMillis();

        return singleId;
    }

    /**
     * Gets the ID without updating access time and hits.
     * Should only be used when the ID is requested "internally", i.e. by mnemosyne itself.
     * A use case is getting the IDs with an LRU or LFU policy: we would not want
     * to modify the access-time or the number of hits.
     */
    public ID getIdInternal(){
        return singleId;
    }
}
