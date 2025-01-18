package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.annotations.UpdateCache;
import com.gmalandrakis.mnemosyne.annotations.UpdateKey;
import com.gmalandrakis.mnemosyne.annotations.UpdatedValue;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MnemoServiceUpdateTest {


    @Test
    public void testUpdate() throws Exception {
        MnemoService mnemoService = new MnemoService();
        var innerClass = new innerUpdateClass();

        var updater = innerUpdateClass.class.getDeclaredMethod("updateTest9", innerUpdateClass.Customer.class);
        var test9 = innerUpdateClass.class.getDeclaredMethod("test9", String.class); //getByAgeAndActivated
        var getByAgeAndActivated = innerUpdateClass.class.getDeclaredMethod("getByAgeAndActivated", int.class, boolean.class);
        mnemoService.generateForMethod(updater);

        var test9Proxy = mnemoService.generateForMethod(test9);
        var getByAgeAndActivatedProxy = mnemoService.generateForMethod(getByAgeAndActivated);

        var cust = new innerUpdateClass.Customer();
        cust.setName("test success!");
        cust.setAge(18);

        var str = UUID.randomUUID();
        cust.setId(str.toString());

        mnemoService.invokeMethodAndUpdate(updater, innerClass, cust);

        assert (mnemoService.tryFetchFromCache(test9Proxy, str.toString()) != null); //tryFetchFromCache exists for this type of test: we would have to rewrite/mock the whole process of generating compoundKeys etc without it.

        var listResult = mnemoService.tryFetchFromCache(getByAgeAndActivatedProxy, 18, false);

        assert (listResult != null); //tryFetchFromCache exists for this type of test: we would have to rewrite/mock the whole process of generating compoundKeys etc without it.
        assert (List.class.isAssignableFrom(listResult.getClass())); //tryFetchFromCache exists for this type of test: we would have to rewrite/mock the whole process of generating compoundKeys etc without it.
        assert (((List) listResult).size() == 1);

    }

      class innerUpdateClass {

         static class Customer {
             private  String name;
             private  String id;
             private Integer age;
             boolean accountActivated;

             public String getName() {
                 return name;
             }

             public void setName(String name) {
                 this.name = name;
             }

             public String getId() {
                 return id;
             }

             public void setId(String id) {
                 this.id = id;
             }

             public Integer getAge() {
                 return age;
             }

             public void setAge(Integer age) {
                 this.age = age;
             }

             public boolean isAccountActivated() {
                 return accountActivated;
             }

             public void setAccountActivated(boolean accountActivated) {
                 this.accountActivated = accountActivated;
             }
         }

        /*
            1. Add special handling for multiple updates and single update
            2.
         */
        @Cached(cacheName = "test9", countdownFromCreation = true)
        public Customer test9(String name) { //updated by updateTest9
            var cust = new Customer();
            cust.setName(name);
            cust.setAge(18);

            return cust;
        } //TODO: Add controls for types

        @Cached(cacheName = "getByAgeAndActivated", countdownFromCreation = true)
        public List<Customer> getByAgeAndActivated(int age, boolean bool) {
            return null;
        }

        @Cached(cacheName = "getByAgeActivatedOnly", countdownFromCreation = true)
        public List<Customer> getByAgeActivatedOnly(int age) {
            return null;
        }

        //   @UpdateCache(name = "getByAgeActivatedOnly", targetObjectKeys = {"age"}, conditionalAdd = "activated", conditionalDelete = "!activated")
        //   @UpdateCache(name = "getByAgeAndActivated", targetObjectKeys = {"age", "accountActivated"})
        @UpdateCache(name = "getByAgeAndActivated", targetObjectKeys = {"age", "accountActivated"})
        //TODO: Ignore null values
        @UpdateCache(name = "test9", targetObjectKeys = "id")
        //TODO: Verify the targetObjectKeys exist in the target class.
        public String updateTest9(@UpdatedValue Customer i) {
            return i.getName();
        }


        @UpdateCache(name = "test9", keys = "testKey", addIfAbsent = true)
        @Cached(cacheName = "testUpdate")
        public String update9Test2(@UpdateKey(name = "testKey") Integer i) {
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

        @UpdateCache(name = "test10", keys = "testKey", addIfAbsent = true)
        public List<String> test10Updater(@UpdatedValue List<String> str, @UpdateKey(name = "testKey") Integer i) {
            return null;
        }

    }
}
