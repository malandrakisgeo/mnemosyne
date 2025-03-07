package com.gmalandrakis.mnemosyne.structures;

public class SingleIdWrapper<ID> extends IdWrapper<ID> {

    ID singleId;

    public SingleIdWrapper(ID singleId){
        this.singleId = singleId;
        this.createdOn = System.currentTimeMillis();
        this.lastAccessed = createdOn;
    }

    public ID getId() {
        hits += 1;
        this.lastAccessed = System.currentTimeMillis();

        return singleId;
    }
}
