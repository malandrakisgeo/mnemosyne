package com.gmalandrakis.mnemosyne.spring;

import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Method interceptor for Spring.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public class SpringInterceptor implements MethodInterceptor {

    private final MnemoService mnemoService;

    SpringInterceptor(MnemoService mnemoService) {
        super();
        this.mnemoService = mnemoService;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        var args = methodInvocation.getArguments();
        if (args.length == 0) { //Sanity check preventing exceptions in case of a forgotten @Cache in a function without arguments.
            return methodInvocation.proceed();
        }
        var method = methodInvocation.getMethod();
        var proxy = mnemoService.getProxies().get(method);
        if (proxy == null) {
            proxy = mnemoService.generateForMethod(method);
        }
        return proxy.invoke(methodInvocation.getThis(), args);
    }
}
