package com.gmalandrakis.mnemosyne.spring;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.annotations.UpdatesValuePool;
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

    public SpringInterceptor(MnemoService mnemoService) {
        super();
        this.mnemoService = mnemoService;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        var args = methodInvocation.getArguments();
        var method = methodInvocation.getMethod();
        var targetObject = methodInvocation.getThis();

        if (method.getAnnotation(Cached.class) != null) {
            return mnemoService.fetchFromCacheOrInvokeMethodAndUpdate(method, args);
        } else if(method.getAnnotation(UpdatesValuePool.class) != null){
            return mnemoService.invokeMethodAndUpdateValuePool(method, targetObject, args);
        } else {
            return mnemoService.invokeMethodAndUpdate(method, targetObject, args);
        }

    }
}
