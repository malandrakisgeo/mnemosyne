package com.gmalandrakis.mnemosyne.exception;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class interceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("yey!");
        return method.invoke(proxy, args);
    }
}
