package com.gmalandrakis.mnemosyne.spring;

import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

public class SpringBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    private final MnemoService mnemoService;

    public SpringBeanPostProcessor(MnemoService mnemoService) {
        this.mnemoService = mnemoService;
    }

    @Override
    public Object postProcessBeforeInstantiation(Class<?> bean, String beanName) {
        mnemoService.generateCachesForClass(bean);
        mnemoService.generateUpdatesForClass(bean);
        return null;
    }
}
