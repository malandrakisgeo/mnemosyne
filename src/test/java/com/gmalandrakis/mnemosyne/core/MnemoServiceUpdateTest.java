package com.gmalandrakis.mnemosyne.core;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.annotations.UpdatesCache;

import com.gmalandrakis.mnemosyne.annotations.UpdateKey;
import com.gmalandrakis.mnemosyne.annotations.UpdatedValue;
import com.gmalandrakis.mnemosyne.structures.AddMode;
import com.gmalandrakis.mnemosyne.structures.RemoveMode;
import org.junit.Test;

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
        mnemoService.generateForMethod(updater, innerClass);

        var test9Proxy = mnemoService.generateForMethod(test9, innerClass);
        var getByAgeAndActivatedProxy = mnemoService.generateForMethod(getByAgeAndActivated, innerClass);

        var cust = new innerUpdateClass.Customer();
        cust.setName("test success!");
        cust.setAge(18);

        var str = UUID.randomUUID();
        cust.setId(str.toString());

        mnemoService.invokeMethodAndUpdate(updater, innerClass, cust);
        //Thread.sleep(200);

        assert (mnemoService.tryFetchFromCache(test9Proxy, str.toString()) != null);

        var listResult = mnemoService.tryFetchFromCache(getByAgeAndActivatedProxy, 18, false);

        assert (listResult != null);
        assert (List.class.isAssignableFrom(listResult.getClass()));
        assert (((List) listResult).size() == 1);

    }

    class innerUpdateClass {

        static class Customer {
            private String name;
            private String id;
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
        @Cached(cacheName = "test9", countdownFromCreation = true, addMode = AddMode.SINGLE_VALUE, removeMode = RemoveMode.SINGLE_VALUE)
        public Customer test9(String name) { //updated by updateTest9
            var cust = new Customer();
            cust.setName(name);
            cust.setAge(18);

            return cust;
        } //TODO: Add controls for types

        @Cached(cacheName = "getByAgeAndActivated", countdownFromCreation = true, addMode = AddMode.ADD_TO_COLLECTION, removeMode = RemoveMode.REMOVE_FROM_COLLECTION)
        public List<Customer> getByAgeAndActivated(int age, boolean bool) {
            return null;
        }

        @Cached(cacheName = "getByAgeActivatedOnly", countdownFromCreation = true, addMode = AddMode.ADD_TO_COLLECTION, removeMode = RemoveMode.REMOVE_FROM_COLLECTION)
        public List<Customer> getByAgeActivatedOnly(int age) {
            return null;
        }

        //   @UpdateCache(name = "getByAgeActivatedOnly", targetObjectKeys = {"age"}, addOnCondition = "activated", conditionalDelete = "!activated")
        //   @UpdateCache(name = "getByAgeAndActivated", targetObjectKeys = {"age", "accountActivated"})
        @UpdatesCache(name = "getByAgeAndActivated", targetObjectKeys = {"age", "accountActivated"}, addMode = AddMode.SINGLE_VALUE, removeMode = RemoveMode.NONE)
        @UpdatesCache(name = "test9", targetObjectKeys = "id", addMode = AddMode.SINGLE_VALUE, removeMode = RemoveMode.NONE)
        public String updateTest9(@UpdatedValue Customer i) {
            return i.getName();
        }


        @UpdatesCache(name = "test9", annotatedKeys = "testKey", addMode = AddMode.SINGLE_VALUE, removeMode = RemoveMode.NONE)
        @Cached(cacheName = "testUpdate", removeMode = RemoveMode.NONE,addMode = AddMode.SINGLE_VALUE )
        public String update9Test2(@UpdateKey(keyId = "testKey") Integer i) {
            if (i == 1) {
                return "Yey!";
            }
            return "Yoy";
        }


        @Cached(cacheName = "test10", countdownFromCreation = true, addMode = AddMode.ADD_TO_COLLECTION, removeMode = RemoveMode.NONE)
        public List<Customer> test10(List<String> ids) {
            var cust1 = new Customer();
            cust1.setId("id1");
            var cust2 = new Customer();
            cust2.setId("id2");
            return List.of(cust1, cust2);

        }

        @UpdatesCache(name = "test10", targetObjectKeys = "id")
        public void saveCustomer(@UpdatedValue Customer newcustomer) {
        }


    }
}
