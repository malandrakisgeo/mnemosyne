package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import com.gmalandrakis.mnemosyne.core.ValuePool;
import com.gmalandrakis.mnemosyne.utils.GeneralUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.*;


public class FIFOTestNewWrapper {
    /*
        1. Mia klash exei methodous pou epistrefoun mia lista me ena object, kai ena object antistoixa.
        2. H mia methodos (list) ginetai invalidated se 2 deuterolepta, h allh den ginetai.
        3. Sigourepse oti meta to eviction h mia FIFOCache den exei tipota, enw h allh exei
        ena value, kai oti to ValuePool den exei ta evicted values ths listas.
     */


    FIFOCache<Integer, Integer, Integer> singleIntegerCache;

    FIFOCache<Integer, Integer, Integer> collectionIntegerCache;

    FIFOCache<Integer, String, testObject> singleTestobjectCache;

    FIFOCache<Integer, String, testObject> collectionTestobjectCache;

    FIFOCache<Integer, String, Integer> separateHandlingCache;

    ValuePool<Integer, Integer> valuePool = new ValuePool<>();

    ValuePool<String, testObject> testObjectValuePool;

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
        singleTestobjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        collectionIntegerCache = new FIFOCache<>(cacheParameters, valuePool);

        cacheParameters.setTimeToLive(20000000);
        cacheParameters.setInvalidationInterval(10000000);
        cacheParameters.setCacheName("fifo-collection");
        cacheParameters.setThreadPoolSize(3);
        cacheParameters.setReturnsCollection(true);
        cacheParameters.setHandleCollectionKeysSeparately(false);

        collectionTestobjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);
        cacheParameters.setHandleCollectionKeysSeparately(true);

        separateHandlingCache = new FIFOCache<>(cacheParameters, testObjectValuePool);

    }

    @Test
    public void test() {
        for (int i = 0; i < 10; i++) {
            var integersToI = this.getIntegersTo(i);
            var integerToI = this.getInteger(i);
            var idmap = GeneralUtils.deduceId(integersToI);

            collectionIntegerCache.putAll(i, (Map) idmap);

            singleIntegerCache.put(i, i, integerToI);
            collectionIntegerCache.remove(i);
        }
        assert (collectionIntegerCache.concurrentFIFOQueue.isEmpty());
        assert (singleIntegerCache.concurrentFIFOQueue.contains(1));

        assert (singleIntegerCache.keyIdMap.get(1) != null);
        assert (valuePool.getValue(1) != null);
        assert (valuePool.getValue(1) != null);

    }


    @Test
    public void testObjects() throws InterruptedException {
        List<Integer> intlst = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var objects = this.getTestObjects(i);
            var object = this.gettestObject(i);
            var ids = (Map) GeneralUtils.deduceId(objects);
            var id = GeneralUtils.deduceId(object);
            intlst.add(i);

            collectionTestobjectCache.putAll(i, ids);
            singleTestobjectCache.put(i, (String) id, object);
        }
        assert (testObjectValuePool.getSize() == 10);

        var value = singleTestobjectCache.get(1);
        var collectionValue = collectionTestobjectCache.getAll(1).stream().toList();

        assert (collectionValue.contains(value));
        assert (collectionValue.size() == 2);

        var value2 = singleTestobjectCache.get(2);
        var collectionValue2 = collectionTestobjectCache.getAll(2).stream().toList();

        assert (collectionValue2.contains(value2));
        assert (collectionValue2.size() == 3);
        assert (!collectionTestobjectCache.concurrentFIFOQueue.isEmpty());

        collectionTestobjectCache.remove(9); //testobject 9 referred to only once by collectiontestobjectcache
        Thread.sleep(1000);
        // assert (testObjectValuePool.getReferences(String.valueOf(9)) == 1);


        var result = getTestObjectsWithList(intlst);
        result.get(0);

    }


    @Test
    public void testFifoFlow() throws InterruptedException {
        cacheParameters.setCapacity(100);
        cacheParameters.setTimeToLive(9999999999L);

        collectionTestobjectCache = new FIFOCache<>(cacheParameters, testObjectValuePool);

        for (int i = 0; i < 100; i++) {
            var objects = this.getTestObjects(i);
            var ids = (Map) GeneralUtils.deduceId(objects);

            collectionTestobjectCache.putAll(i, ids);
        }
        assert (testObjectValuePool.getSize() == 100);
        var objects = this.getTestObjects(200);
        var ids = (Map) GeneralUtils.deduceId(objects);
        collectionTestobjectCache.putAll(200, ids);
        System.out.println(collectionTestobjectCache.concurrentFIFOQueue.size());
        assert (collectionTestobjectCache.concurrentFIFOQueue.size() == 100);
        assert (collectionTestobjectCache.getAll(0).isEmpty());
//TODO the same for concurrent reads and writes
    }


    @Test
    public void testSpeed() {
        System.out.println(System.currentTimeMillis());

        for (int i = 0; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            var id = (Map) GeneralUtils.deduceId(integersToI);
            collectionIntegerCache.putAll(i, id);
        }

        for (int i = 0; i < 10000; i++) {
            var integerToI = this.getInteger(i);
            var id = GeneralUtils.deduceId(integerToI);

            singleIntegerCache.put(i, (Integer) id, integerToI);
        }
        System.out.println(System.currentTimeMillis());

        for (int i = 0; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            var id = (Map) GeneralUtils.deduceId(integersToI);
            separateHandlingCache.putAll(i, id);
        }
        System.out.println(System.currentTimeMillis());

        var bol = separateHandlingCache.idUsedAlready("1");
        System.out.println(bol);
        System.out.println(System.currentTimeMillis());


    }


    private List<Integer> getIntegersTo(int i) {
        if (i <= 0) {
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
