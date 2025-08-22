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
        var instance = new innerClass();

        mnemoService.generateForMethod(innerClass.class.getDeclaredMethod("test11", List.class), instance);

        var millis = System.currentTimeMillis();

        for (int i = 1; i < 1000; i++) {
            var integersToI = this.getIntegersTo(i);
            var result = (List) mnemoService.fetchFromCacheOrInvokeMethodAndUpdate(innerClass.class.getDeclaredMethod("test11", List.class),  integersToI);
            //var result = (List) mnemoproxy11.fetchFromCacheOrInvokeMethod(instance, integersToI);
            if(result.size() != integersToI.size()){
                System.out.println(result);
                System.out.println(integersToI);

            }
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
        var proxy10 = Mockito.spy(spyMnemo.generateForMethod(test10, innerClass));

        var field = spyMnemo.getClass().getDeclaredField("proxies");
        field.setAccessible(true); // !!!
        var field2 = spyMnemo.getClass().getDeclaredField("valuePoolConcurrentHashMap");
        field2.setAccessible(true); // !!!
        var field3 = proxy10.getClass().getDeclaredField("cache");
        field3.setAccessible(true); // !!!


        var proxies = (ConcurrentHashMap) field.get(spyMnemo);
        proxies.put(test10, proxy10);

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10,  1);
        verify(proxy10, times(1)).getFromUnderlyingMethodAndUpdateMainCache(any());
        verify(proxy10, times(1)).getFromCache(any());

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10,  1);
        verify(proxy10, times(1)).getFromUnderlyingMethodAndUpdateMainCache(any()); //It was called on the previous try! TODO: Find a way to nullify without resetting
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

        spyMnemo.fetchFromCacheOrInvokeMethodAndUpdate(test10,  1);
        verify(proxy10, times(2)).getFromUnderlyingMethodAndUpdateMainCache(any());
        verify(proxy10, times(3)).getFromCache(any());

    }

    @Test
    public void assertThrows_runtime_concreteSepareteCollection() throws NoSuchMethodException {
        MnemoService mnemoService = new MnemoService();
        var instance = new innerClass();
        var impossibleReturnTypeForSeparate = innerClass.class.getDeclaredMethod("test6", Collection.class);
        var acceptableTypes = innerClass.class.getDeclaredMethod("test12", List.class, int.class);
        var impossibleKeyForSeparate = innerClass.class.getDeclaredMethod("test13", List.class, int.class);

        assertThrows(MnemosyneInitializationException.class, () -> {
            mnemoService.generateForMethod(impossibleReturnTypeForSeparate, instance);
        });
        mnemoService.generateForMethod(acceptableTypes, instance); //should not throw
        assertThrows(MnemosyneRuntimeException.class, () -> {
            mnemoService.generateForMethod(impossibleKeyForSeparate, instance);
        });

    }

    @Test
    public void assertThrows_runtime_nameAlreadyExists() throws NoSuchMethodException {
        MnemoService mnemoService = new MnemoService();

        boolean success = false;
        var method4 = innerClass.class.getDeclaredMethod("test4");
        var method7 = innerClass.class.getDeclaredMethod("test7");
        var instance = new innerClass();

        try {
            mnemoService.generateForMethod(method4, instance);

            mnemoService.generateForMethod(method7, instance);

        } catch (RuntimeException e) {
            //success!
            if (e.getMessage().contains("same name"))
                success = true;
        }

        assert (success);
    }

    @Test
    public void testValuePoolTypes() throws Exception{
        MnemoService mnemoService = new MnemoService();
        var function = innerClass.class.getDeclaredMethod("functionForPreemptiveUpdateTest", Integer.class);
        var instance = new innerClass();
        mnemoService.generateForMethod(function, instance);
        mnemoService.generateUpdatesForBean(instance);

        var stringValuePool1 = innerClass.class.getDeclaredMethod("testpreemptiveUpdateWithList", List.class, Integer.class);
        var stringValuePool2 = innerClass.class.getDeclaredMethod("testpreemptiveUpdate", String.class, Integer.class);
        var intValuePool1 = innerClass.class.getDeclaredMethod("test11", List.class);
        var intValuePool2 = innerClass.class.getDeclaredMethod("test12", List.class, int.class);
        var intValuePool3 = innerClass.class.getDeclaredMethod("test13", List.class, int.class);
        mnemoService.generateForMethod(intValuePool1, instance);


        assert(mnemoService.getValuePool(function).equals(mnemoService.getValuePool(stringValuePool1)));
        assert(mnemoService.getValuePool(stringValuePool1).equals(mnemoService.getValuePool(stringValuePool2)));

        assert(!mnemoService.getValuePool(function).equals(mnemoService.getValuePool(intValuePool1)));

        assert(mnemoService.getValuePool(intValuePool1).equals(mnemoService.getValuePool(intValuePool2)));
        assert(mnemoService.getValuePool(intValuePool2).equals(mnemoService.getValuePool(intValuePool3)));

    }

    @Test
    public void testPreemptiveUpdate() throws Exception {
        MnemoService mnemoService = new MnemoService();
        var function = innerClass.class.getDeclaredMethod("functionForPreemptiveUpdateTest", Integer.class);
        var updater1 = innerClass.class.getDeclaredMethod("testpreemptiveUpdateWithList", List.class, Integer.class);
        var updater2 = innerClass.class.getDeclaredMethod("testpreemptiveUpdate", String.class, Integer.class);
        var instance = new innerClass();
        mnemoService.generateForMethod(function, instance);
        mnemoService.generateUpdatesForBean(instance);
        mnemoService.invokeMethodAndUpdate(updater2, instance, "extra string", 1);
        Thread.sleep(500);

        List<String> result = (List<String>) mnemoService.fetchFromCacheOrInvokeMethodAndUpdate(function, 1);

        assert (result.size() == 4);
        assert (result.contains("extra string"));

        mnemoService.invokeMethodAndUpdate(updater1, instance, List.of("val3"), 2);
         result = (List<String>) mnemoService.fetchFromCacheOrInvokeMethodAndUpdate(function, 2);

        assert (result.size() == 4);
        assert (result.contains("val3"));
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


        @Cached(cacheName = "cache4")
        public Object test4() {
            return null;
        }

        @Cached(cacheName = "should throw a Runtime - separate handling with concrete implementation of List", allowSeparateHandlingForKeyCollections = true)
        ArrayList<Object> test6(Collection<String> col) {
            return null;
        }

        @Cached(cacheName = "cache4")
            //same name as test4 = runtime
        Set<Object> test7() {
            return null;
        }

        @Cached(cacheName = "separateHandling", allowSeparateHandlingForKeyCollections = true)
        Collection<Object> test8(Collection<String> col) {
            return null;
        }


        @Cached(cacheName = "test9", countdownFromCreation = true)
        public String test9(Integer i) { //updated by updateTest9
            return null;
        }

        @UpdatesCache(name = "test9", annotatedKeys = "testKey", removeMode = UpdatesCache.RemoveMode.REMOVE_VALUE_FROM_COLLECTION, addMode = UpdatesCache.AddMode.NONE)
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

        @Cached(cacheName = "testPreemptiveUpdate", countdownFromCreation = true)
        public List<String> functionForPreemptiveUpdateTest(Integer i) {
            return List.of("val1", "val2", String.valueOf(i));
        }

        @UpdatesCache(name = "testPreemptiveUpdate", annotatedKeys = "testKey", addMode = UpdatesCache.AddMode.DEFAULT, removeMode = UpdatesCache.RemoveMode.NONE)
        public void testpreemptiveUpdateWithList(@UpdatedValue List<String> str, @UpdateKey(keyId = "testKey") Integer i) {
        }

        @UpdatesCache(name = "testPreemptiveUpdate", annotatedKeys = "testKey", addMode = UpdatesCache.AddMode.ADD_VALUES_TO_COLLECTION, removeMode = UpdatesCache.RemoveMode.NONE)
        public void testpreemptiveUpdate(@UpdatedValue String str, @UpdateKey(keyId = "testKey") Integer i) {
        }

        @Cached(cacheName = "separateHandlingToBeTested", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
       public List<Integer> test11(List<Integer> integers) { //stupid function
            return integers;
        }

        @Cached(cacheName = "separateHandlingWithKeyToBeTested", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
        public List<Integer> test12(@Key List<Integer> integers, int i) { //stupid function
            return integers;
        }

        @Cached(cacheName = "separateHandlingWithKeyToBeTestedAndThrow", capacity = 500, allowSeparateHandlingForKeyCollections = true, threadPoolSize = 5)
        public  List<Integer> test13(List<Integer> integers, int i) { //shouldThrow
            return integers;
        }

    }
}
