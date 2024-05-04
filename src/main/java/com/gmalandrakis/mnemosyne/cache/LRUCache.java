package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;

public class LRUCache<K,V> extends AbstractGenericCache<K, V> {
    //Coming soon!
    public LRUCache(CacheParameters cacheParameters) {
        super(cacheParameters);
    }

    @Override
    public String getAlgorithmName() {
        return null;
    }

    @Override
    public K getTargetKey() {
        return null;
    }

    @Override
    public void evict() {

    }
}
