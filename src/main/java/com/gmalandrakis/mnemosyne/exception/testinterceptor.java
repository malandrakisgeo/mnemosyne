package com.gmalandrakis.mnemosyne.exception;

import java.lang.reflect.Proxy;

public class testinterceptor {

    public static void main(String[] args) {
        var handler = new interceptor();
        crashTest f = (crashTest) Proxy.newProxyInstance(crashTest.class.getClassLoader(),
                new Class[]{crashTest.class},
                handler);
        f.hashCode();
    }
}
