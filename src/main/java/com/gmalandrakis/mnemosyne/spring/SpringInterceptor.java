package com.gmalandrakis.mnemosyne.spring;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.core.MnemoProxy;
import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;

public class SpringInterceptor implements MethodInterceptor {

    private MnemoService mnemoService;

    SpringInterceptor(MnemoService mnemoService) {
        super();
        this.mnemoService = mnemoService;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        var args = methodInvocation.getArguments();
        if(args == null || args.length == 0){ //Sanity check preventing exceptions in case of a forgotten @Cache in a function without arguments.
            return methodInvocation.proceed();
        }
        var method = methodInvocation.getMethod();
        var proxy = mnemoService.getProxies().get(method);
        if (proxy == null) {
            proxy = mnemoService.generateForMethod(method); //Assumes not null, since null is returned whenever a @Cache is absent, and spring only calls this (?) if and only if one is present
        }
        return proxy.invoke(methodInvocation.getThis(), args);
    }
}
