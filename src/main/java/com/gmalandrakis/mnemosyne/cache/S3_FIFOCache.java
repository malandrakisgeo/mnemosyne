package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import com.gmalandrakis.mnemosyne.structures.SingleIdWrapper;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class S3_FIFOCache<K, ID, T> extends AbstractGenericCache<K, ID, T> {
    final ConcurrentLinkedQueue<K> ghost = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<K> main = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<K> small = new ConcurrentLinkedQueue<>();

    final ConcurrentHashMap<ID, Integer> numberOfUsesById = new ConcurrentHashMap<ID, Integer>();

    public S3_FIFOCache(CacheParameters parameters, ValuePool<ID, T> valuePool) {
        super(parameters, valuePool);
    }

    @Override
    public void put(K key, ID id) {
        if (key == null || id == null) {
            return;
        }
        if (actualSize() >= this.actualCapacity) {
            this.evict();
        }

        if (returnsCollection) {
            //TODO
        } else {
            keyIdMapper.put(key, new SingleIdWrapper<ID>(id, 3));

            if (ghost.contains(key)) {
                var curGh = keyIdMapper.get(key);
                main.add(key); //TODO: Head instead of tail
                ghost.remove(key);
            } else {
                small.add(key);
            }
        }
    }

    @Override
    public void putAll(K key, Collection<ID> ídValueMap) {

    }

    @Override
    public void putInAllCollections(ID id) {

    }

    @Override
    public Collection<T> getAll(K key) {
        return List.of();
    }

    @Override
    public Collection<T> getAll(Collection<K> key) {
        return List.of();
    }

    @Override
    public T get(K key) {
        if (!returnsCollection || handleCollectionKeysSeparately) {
            var result = keyIdMapper.get(key);
            if(result == null){
                return null;
            }
            if (small.contains(key) || main.contains(key)) {
            } else {

            }
            ID id = (ID) (handleCollectionKeysSeparately ? ((CollectionIdWrapper) key).getIds().toArray()[0] : ((SingleIdWrapper) key).getId()); //TODO: Frequency cap: 3. Prosarmozeis ta hits

            return valuePool.getValue(id);
        }

        return null;
    }

    @Override
    public void remove(K key) {

    }

    @Override
    public void removeOneFromCollection(K key, ID id) {

    }

    @Override
    public void removeById(Collection<ID> ids) {

    }

    @Override
    public String getAlgorithmName() {
        return "S3-FIFO";
    }

    @Override
    public K getTargetKey() {
        return null;
    }

    @Override
    public void evict() {

    }

    @Override
    public void invalidateCache() {

    }

    @Override
    public boolean idUsedAlready(ID id) {
        return false;
    }

    private int actualSize() {
        return ghost.size() + main.size() + small.size();
    }

    private void evictM(){

    }
    private void evictS(){

    }
}
