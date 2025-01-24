package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.*;
import com.gmalandrakis.mnemosyne.cache.AbstractGenericCache;
import com.gmalandrakis.mnemosyne.exception.MnemosyneInitializationException;
import com.gmalandrakis.mnemosyne.exception.MnemosyneRuntimeException;
import com.gmalandrakis.mnemosyne.structures.CollectionIdWrapper;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MnemoServiceTest {

    @Test
    public void testValuePools() throws Throwable {

        CollectionIdWrapper c = new CollectionIdWrapper(new HashSet<Integer>(1));
        c.addToCollectionOrUpdate(1);
        c.addToCollectionOrUpdate(2);

        MnemoService mnemoService = new MnemoService();

        var handlesCollections = Collection.class.isAssignableFrom(innerClass.class.getDeclaredMethod("test6", Collection.class).getReturnType());

        assert (handlesCollections);
        var mnemoproxy1 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test1"));
        var mnemoproxy2 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test2"));
        var mnemoproxy3 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test3"));
        var mnemoproxy4 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test4"));
        var mnemoproxy8 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test8", Collection.class));
        var mnemoproxy9 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test9", Integer.class));
        var mnemoproxy10 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test10", Integer.class));

        assert (mnemoproxy1.getValuePool() == mnemoproxy2.getValuePool());
        assert (mnemoproxy1.getValuePool().hashCode() == mnemoproxy2.getValuePool().hashCode());
        assert (mnemoproxy3.getValuePool() == mnemoproxy2.getValuePool());
        assert (mnemoproxy4.getValuePool() == mnemoproxy2.getValuePool());
        assert (mnemoproxy8.getValuePool() == mnemoproxy2.getValuePool());
        assert (mnemoproxy9.getValuePool() != mnemoproxy2.getValuePool());
        assert (mnemoproxy9.getValuePool() == mnemoproxy10.getValuePool());

        var instance = new innerClass();
        var mnemoproxy11 = mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test11", List.class));

        var millis = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            var result = (List) mnemoService.fetchFromCacheOrInvokeMethodAndUpdate(innerClass.class.getDeclaredMethod("test11", List.class), instance, integersToI);
            //var result = (List) mnemoproxy11.fetchFromCacheOrInvokeMethod(instance, integersToI);
            assert (result.size() == integersToI.size());
        }
        var secs = System.currentTimeMillis() - millis;
        System.out.println(secs / 1000);

    }

    @Test
    public void generalFlowTest() throws Throwable {
        /*
            1. Creates an instance of innerClass, and spies on it.
            2. Calls some of its' functions.
            3. Then recalls them with the same arguments and verifies the cache was called instead of the underlying function:.
         */
        var mnemo = new MnemoService();
        var innerClass = new innerClass();

        var spyInnerClass = Mockito.spy(innerClass);
        var spyMnemo = Mockito.spy(mnemo);
        var test10 = innerClass.class.getDeclaredMethod("test10", Integer.class);
        var proxy10 = Mockito.spy(spyMnemo.generateForMethod(test10));

        var field = spyMnemo.getClass().getDeclaredField("proxies");
        field.setAccessible(true); // !!!
        var field2 = spyMnemo.getClass().getDeclaredField("valuePoolConcurrentHashMap");
        field2.setAccessible(true); // !!!
        var field3 = proxy10.getClass().getDeclaredField("cache");
        field3.setAccessible(true); // !!!


        var proxies = (ConcurrentHashMap) field.get(spyMnemo);
        proxies.put(test10, proxy10);

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10, spyInnerClass, 1);
        verify(proxy10, times(1)).getFromUnderlyingMethodAndUpdateMainCache(any(), any());
        verify(proxy10, times(1)).getFromCache(any());

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10, spyInnerClass, 1);
        verify(proxy10, times(1)).getFromUnderlyingMethodAndUpdateMainCache(any(), any()); //It was called on the previous try! TODO: Find a way to nullify without resetting
        verify(proxy10, times(2)).getFromCache(any());


        /*
            1. Invalidates the cache
            2. Makes sure the values have been evicted from the valuePool too
         */
        var cache = (AbstractGenericCache) field3.get(proxy10);
        var valuePools = (ConcurrentHashMap) field2.get(spyMnemo);
        var valuePool = (ValuePool) valuePools.get("java.lang.String");

        cache.invalidateCache();
        Thread.sleep(500);
        assert (valuePool.getSize() == 0);

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10, spyInnerClass, 1);
        verify(proxy10, times(2)).getFromUnderlyingMethodAndUpdateMainCache(any(), any());
        verify(proxy10, times(3)).getFromCache(any());

    }

    @Test
    public void assertThrows_runtime_concreteSepareteCollection() throws NoSuchMethodException {
        MnemoService mnemoService = new MnemoService();

        var impossibleReturnTypeForSeparate = innerClass.class.getDeclaredMethod("test6", Collection.class);
        var acceptableTypes = innerClass.class.getDeclaredMethod("test12", List.class, int.class);
        var impossibleKeyForSeparate = innerClass.class.getDeclaredMethod("test13", List.class, int.class);

        assertThrows(MnemosyneInitializationException.class, () -> {
            mnemoService.generateForMethod(impossibleReturnTypeForSeparate);
        });
        mnemoService.generateForMethod(acceptableTypes); //should not throw
        assertThrows(MnemosyneRuntimeException.class, () -> {
            mnemoService.generateForMethod(impossibleKeyForSeparate);
        });

    }

    @Test
    public void assertThrows_runtime_nameAlreadyExists() throws NoSuchMethodException {
        MnemoService mnemoService = new MnemoService();

        boolean success = false;
        var method4 = innerClass.class.getDeclaredMethod("test4");
        var method7 = innerClass.class.getDeclaredMethod("test7");

        try {
            mnemoService.generateForMethod(method4);

            mnemoService.generateForMethod(method7);

        } catch (RuntimeException e) {
            //success!
            if (e.getMessage().contains("same name"))
                success = true;
        }

        assert (success);
    }


    /**/


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


    class innerClass {
        @Cached(cacheName = "cache1")
        public HashSet<Set<ValuePool>> test1() {
            return null;
        }

        @Cached(cacheName = "cache2")
        public Collection<Map<ValuePool, Object>> test2() {
            return null;
        }

        @Cached(cacheName = "cache3")
        public Collection<List<ValuePool>> test3() {
            return null;
        }

        @Cached(cacheName = "cache4")
        public ValuePool test4() {
            return null;
        }

        @Cached(cacheName = "should throw a Runtime - separate handling with concrete implementation of List", allowSeparateHandlingForKeyCollections = true)
        ArrayList<ValuePool> test6(Collection<String> col) {
            return null;
        }

        @Cached(cacheName = "cache4")
            //same name as test4 = runtime
        Set<ValuePool> test7() {
            return null;
        }

        @Cached(cacheName = "separateHandling", allowSeparateHandlingForKeyCollections = true)
        Collection<ValuePool> test8(Collection<String> col) {
            return null;
        }


        @Cached(cacheName = "test9", countdownFromCreation = true)
        public String test9(Integer i) { //updated by updateTest9
            return null;
        }

        @UpdatesCache(name = "test9", annotatedKeys = "testKey")
        @Cached(cacheName = "testUpdate")
        public String updateTest9(@UpdateKey(keyId = "testKey") Integer i) {
            if (i == 1) {
                return "Yey!";
            }
            return "Yoy";
        }


        @Cached(cacheName = "test10", countdownFromCreation = true)
        public List<String> test10(Integer i) {

            if (i % 2 == 0) {
                return Collections.singletonList("Yey!");
            }
            return Collections.singletonList("Yoy");
        }

        @UpdatesCache(name = "test10", annotatedKeys = "testKey")
        public List<String> test10Updater(@UpdatedValue List<String> str, @UpdateKey(keyId = "testKey") Integer i) {
            return null;
        }

        @Cached(cacheName = "separateHandlingToBeTested", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
        List<Integer> test11(List<Integer> integers) { //stupid function
            return integers;
        }

        @Cached(cacheName = "separateHandlingWithKeyToBeTested", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
        List<Integer> test12(@Key List<Integer> integers, int i) { //stupid function
            return integers;
        }

        @Cached(cacheName = "separateHandlingWithKeyToBeTestedAndThrow", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
        List<Integer> test13(List<Integer> integers, int i) { //shouldThrow
            return integers;
        }

    }
}
