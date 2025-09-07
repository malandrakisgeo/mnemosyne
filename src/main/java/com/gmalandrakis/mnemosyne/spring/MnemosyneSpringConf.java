package com.gmalandrakis.mnemosyne.spring;

import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan
@EnableAspectJAutoProxy
public class MnemosyneSpringConf {
    private final MnemoService mnemoService = new MnemoService();
    private final SpringInterceptor springInterceptor = new SpringInterceptor(mnemoService);
    @Bean
    public SpringBeanPostProcessor springBeanPostProcessor() {
        return new SpringBeanPostProcessor(mnemoService);
    }

    @Bean
    public Advisor cacheAdvisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.gmalandrakis.mnemosyne.annotations.Cached) " +
                "|| @annotation(com.gmalandrakis.mnemosyne.annotations.UpdatesCache) " +
                "|| @annotation(com.gmalandrakis.mnemosyne.annotations.UpdatesCaches)" +
                "|| @annotation(com.gmalandrakis.mnemosyne.annotations.UpdatesValuePool) ");
        return new DefaultPointcutAdvisor(pointcut, springInterceptor);
    }

}
