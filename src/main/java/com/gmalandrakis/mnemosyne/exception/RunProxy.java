package com.gmalandrakis.mnemosyne.exception;

import java.util.UUID;

public class RunProxy {

    public static void main(String[] args) throws Throwable {
        crashTest obj = new crashTest();
        var meth = crashTest.class.getMethod("params", int.class, String.class, UUID.class);

        //MnemoProxy proxyService = new MnemoProxy(null,null);
       // proxyService.invoke(obj, meth, 1, "param", UUID.randomUUID());
    }
}
