package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.structures.*;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


public class FIFOTest {
    /*
        1. Mia klash exei methodous pou epistrefoun mia lista me ena object, kai ena object antistoixa.
        2. H mia methodos (list) ginetai invalidated se 2 deuterolepta, h allh den ginetai.
        3. Sigourepse oti meta to eviction h mia FIFOCache den exei tipota, enw h allh exei
        ena value, kai oti to ValuePool den exei ta evicted values ths listas.
     */


    FIFOCache<Integer, Integer, Integer> singleIntegerCache;

    FIFOCache<Integer, Integer, Integer> collectionIntegerCache;

    FIFOCache<Integer, Object, testObject> singleTestObjectCache;

    FIFOCache<Integer, String, testObject> collectionTestObjectCache;

    FIFOCache<Integer, String, Integer> separateHandlingCache;
    FIFOCache<Integer, String, Integer> separateHandlingCache2;

    ValuePool<Integer, Integer> valuePool = new ValuePool<>();

    ValuePool<Object, testObject> testObjectValuePool;

    private final int totalItems = 150000;

    private CacheParameters cacheParameters;

    @Before
    public void beforeTest() throws NoSuchFieldException, IllegalAccessException {
        testObjectValuePool = new ValuePool<>();
        cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(totalItems - 1);
        cacheParameters.setTimeToLive(1500000);
        cacheParameters.setInvalidationInterval(200000000);
        cacheParameters.setCacheName("fifo-single");
        cacheParameters.setThreadPoolSize(2);
        singleIntegerCache = new FIFOCache<>(cacheParameters, valuePool);
        singleTestObjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        cacheParameters.setReturnsCollection(true);
        collectionIntegerCache = new FIFOCache<>(cacheParameters, valuePool);

        cacheParameters.setTimeToLive(20000000);
        cacheParameters.setInvalidationInterval(10000000);
        cacheParameters.setCacheName("fifo-collection");
        cacheParameters.setThreadPoolSize(3);
        cacheParameters.setReturnsCollection(true);
        cacheParameters.setHandleCollectionKeysSeparately(false);

        collectionTestObjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        cacheParameters.setHandleCollectionKeysSeparately(true);

        cacheParameters.setThreadPoolSize(50);
        separateHandlingCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        separateHandlingCache2 = new FIFOCache<>(cacheParameters, testObjectValuePool);
    }

    @Test
    public void verify__removalFromValuePool() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            var integersToI = this.getIntegersTo(i);
            var idmap = (Map) GeneralUtils.deduceId(integersToI);
            var integer = this.getInteger(i);

            valuePool.updateValueOrPutPreemptively(i, integer);
            collectionIntegerCache.putAll(i,  idmap.keySet());

            singleIntegerCache.put(i, i);
            collectionIntegerCache.remove(i);
        }
        assert (collectionIntegerCache.concurrentFIFOQueue.isEmpty());
        assert (singleIntegerCache.concurrentFIFOQueue.contains(1));

        assert (singleIntegerCache.keyIdMapper.get(1) != null);
        assert (valuePool.getValue(1) != null);
        assert (valuePool.getNumberOfUsesForId(1) == 1);


        singleIntegerCache.remove(1);
        Thread.sleep(150);
        assert (valuePool.getValue(1) == null);
        assert (valuePool.getNumberOfUsesForId(1) == 0);
    }

    @Test
    public void verifyNullUsesRemovedFromValuePool() {
        //TODO
    }


    @Test
    public void testFlowWithObjects() throws InterruptedException {
        List<Integer> intlst = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var objects = this.getTestObjects(i); //returns an empty list on zero!
            var object = this.gettestObject(i);
            var ids = (Map) GeneralUtils.deduceId(objects);
            var id = GeneralUtils.deduceId(object);
            intlst.add(i);
            testObjectValuePool.updateValueOrPutPreemptively(id, object);

            collectionTestObjectCache.putAll(i, ids.keySet());
            singleTestObjectCache.put(i, id);
        }
        assert (testObjectValuePool.getSize() == 10);

        var id = GeneralUtils.deduceId( this.gettestObject(0));
        assert (testObjectValuePool.getNumberOfUsesForId(id) == 2);
        assert (collectionTestObjectCache.numberOfUsesById.get(id) == 9);
        assert (singleTestObjectCache.numberOfUsesById.get(id) == 1);

        var value = singleTestObjectCache.get(1);
        var collectionValue = collectionTestObjectCache.getAll(1).stream().toList();

        assert (collectionValue.contains(value));
        assert (collectionValue.size() == 2);

        var value2 = singleTestObjectCache.get(2);
        var collectionValue2 = collectionTestObjectCache.getAll(2).stream().toList();

        assert (collectionValue2.contains(value2));
        assert (collectionValue2.size() == 3);
        assert (!collectionTestObjectCache.concurrentFIFOQueue.isEmpty());

        collectionTestObjectCache.remove(9); //testobject 9 referred to only once by collectiontestobjectcache
        singleTestObjectCache.remove(9); //testobject 9 referred to only once by collectiontestobjectcache

        Thread.sleep(500);
        assert (testObjectValuePool.getValue(String.valueOf(9)) == null);
        // assert (singleTestobjectCache.numberOfUsesById.get(String.valueOf(9)) == null); TODO

        singleTestObjectCache.invalidateCache();
        collectionTestObjectCache.invalidateCache();
    }

    @Test
    public void testFifoUpdatesOnExistingKeys(){
        singleTestObjectCache.invalidateCache();
        collectionTestObjectCache.invalidateCache();

        var testobj = this.gettestObject(1);
        var testobj2 = this.gettestObject(2);

        var id = GeneralUtils.deduceId(testobj);
        var id2 = GeneralUtils.deduceId(testobj2);
        testObjectValuePool.updateValueOrPutPreemptively(id, testobj);
        singleTestObjectCache.put(1, id);
        assert (singleTestObjectCache.numberOfUsesById.get(id) == 1);
        assert (singleTestObjectCache.get(1).equals(testobj));
        assert (testObjectValuePool.getNumberOfUsesForId(id) == 1);

        //using the same key and id with an updated value should be possible
        testObjectValuePool.updateValueOrPutPreemptively(id, testobj2);

        singleTestObjectCache.put(1, id);
        assert (singleTestObjectCache.numberOfUsesById.get(id) == 1);
        assert (testObjectValuePool.getValue(id).equals(testobj2));
        assert (testObjectValuePool.getNumberOfUsesForId(id) == 1);

        //using the same key with a brand new id/value should be possible.
        testObjectValuePool.updateValueOrPutPreemptively(id2, testobj2);

        singleTestObjectCache.put(1, id2);
        assert (singleTestObjectCache.numberOfUsesById.get(id) == null);
        assert (testObjectValuePool.getNumberOfUsesForId(id) == 0);
        assert (singleTestObjectCache.numberOfUsesById.get(id2) == 1);
        assert (testObjectValuePool.getNumberOfUsesForId(id2) == 1);
        assert (singleTestObjectCache.get(1).equals(testobj2));
    }


    @Test
    public void testFifoFlow() throws InterruptedException {
        cacheParameters.setCapacity(100);
        cacheParameters.setTimeToLive(9999999999L);
        cacheParameters.setPreemptiveEvictionPercentage((short) 100);

        collectionTestObjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);

        for(int i = 0; i < 100; i++){
            var object = this.gettestObject(i);
            var id = (String) GeneralUtils.deduceId(object);
            collectionTestObjectCache.putAll(i, Set.of(id));
        }
       // assert (testObjectValuePool.getSize() == 100); //once upon a time the caches were putting new objects to the value pools!
        var object = this.gettestObject(101);
        var id = (String) GeneralUtils.deduceId(object);
        collectionTestObjectCache.putAll(100, Set.of(id));
        assert (collectionTestObjectCache.numberOfUsesById.size() == 100);
        assert (collectionTestObjectCache.getAll(0).isEmpty());
    }


    @Test
    public void test_separateCacheHandling() throws Exception {

        System.out.println(System.currentTimeMillis());
        var valMapField = valuePool.getClass().getDeclaredField("valueMap");
        valMapField.setAccessible(true);
        var valueMap = (ConcurrentHashMap<?, CacheValue<?>>) valMapField.get(collectionIntegerCache.valuePool);
        for (int i = 0; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            Map<Integer,Integer> id = (Map<Integer,Integer>) GeneralUtils.deduceId(integersToI);
            id.forEach(collectionIntegerCache.valuePool::updateValueOrPutPreemptively);
            collectionIntegerCache.putAll(i, id.keySet());
        }
        assert (valueMap.get(0).getNumberOfUses() == 1); //only in one cache
        assert (collectionIntegerCache.numberOfUsesById.get(0) == 1000); //but used in by a thousand keys!

        collectionIntegerCache.invalidateCache();
        assert (valueMap.get(0) == null);

        for (int i = 0; i < 10000; i++) {
            var integerToI = this.getInteger(i);
            var id = GeneralUtils.deduceId(integerToI);
            singleIntegerCache.valuePool.updateValueOrPutPreemptively((Integer) id, integerToI);

            singleIntegerCache.put(i, (Integer) id);
        }
        System.out.println(System.currentTimeMillis());


        testObjectValuePool = new ValuePool<>();
        valueMap = (ConcurrentHashMap<?, CacheValue<?>>) valMapField.get(testObjectValuePool);
        cacheParameters.setThreadPoolSize(10);
        separateHandlingCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        separateHandlingCache2 = new FIFOCache<>(cacheParameters, testObjectValuePool);

        /*
            The test below:
            1. Iteratively generates a 0<N<1000 lists of integers that contain values from 1 to N
            2. Deduces the IDs of those lists after treating them as string values.
            3. Puts all lists and ids in two separateHandling-caches
            4. verifies  the value pool has correct number of cachesUsingValue after putting, and after invalidating in one cache
            5. Gives us some hints about the speed to do all these in separateHandling caches
         */
        for (int i = 0; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            Map<String, String> id = (Map<String, String>) GeneralUtils.deduceId(integersToI.stream().map(String::valueOf).toList());
            id.forEach((a,b)->separateHandlingCache.valuePool.updateValueOrPutPreemptively(a,(Integer.valueOf(b))));

            separateHandlingCache.putAll(i, id.keySet());
            separateHandlingCache2.putAll(i, id.keySet());

        }
        var randonum1 = ThreadLocalRandom.current().nextInt(0, 1001);
        var randonum2 = ThreadLocalRandom.current().nextInt(0, 1001);

        assert (valueMap.get(String.valueOf(randonum1)).getNumberOfUses() == 2);
        assert (valueMap.get(String.valueOf(randonum2)).getNumberOfUses() == 2);

        separateHandlingCache.invalidateCache();

        System.out.println(System.currentTimeMillis());

        assert (valueMap.get(String.valueOf(randonum1)).getNumberOfUses() == 1);
        assert (valueMap.get(String.valueOf(randonum2)).getNumberOfUses() == 1);


        assert (!separateHandlingCache.idUsedAlready("1"));
        assert (separateHandlingCache2.idUsedAlready("1"));
    }


    private List<Integer> getIntegersTo(int i) {
        if (i < 0) {
            return Collections.emptyList();
        }
        var lst = new ArrayList<Integer>();
        for (int j = 0; j <= i; ++j) {
            lst.add(j);
        }
        return lst;
    }

    private Integer getInteger(int i) {
        return Integer.valueOf(i);
    }

    private List<testObject> getTestObjects(int i) {
        if (i <= 0) {
            return Collections.emptyList();
        }
        var lst = new ArrayList<testObject>();
        for (int j = 0; j <= i; ++j) {
            var obj = new testObject();
            obj.id = String.valueOf(j);
            obj.name = "TEST TESTSSON" + j;
            lst.add(obj);
        }
        return lst;
    }

    private List<testObject> getTestObjectsWithList(List<Integer> ints) {
        if (ints == null || ints.isEmpty()) {
            return Collections.emptyList();
        }
        var lst = new ArrayList<testObject>();
        ints.forEach(i -> {
            lst.add(gettestObject(i));
        });
        return lst;
    }

    private testObject gettestObject(int i) {
        var obj = new testObject();
        obj.id = String.valueOf(i);
        obj.name = "TEST TESTSSON" + i;
        return obj;
    }


    class testObject {
        String id;
        String name;
        String email;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof testObject object)) return false;
            return Objects.equals(id, object.id) && Objects.equals(name, object.name) && Objects.equals(email, object.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, email);
        }
    }

}
