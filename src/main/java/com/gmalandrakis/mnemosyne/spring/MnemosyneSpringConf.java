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
    public Advisor cacheAdvisor() {  //TODO: Find the proper pointcut expression and merge cacheAdvisor and updateAdvisor into one
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.gmalandrakis.mnemosyne.annotations.Cached)");
        return new DefaultPointcutAdvisor(pointcut, springInterceptor);
    }

    @Bean
    public Advisor updateAdvisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.gmalandrakis.mnemosyne.annotations.UpdateCache)");
        return new DefaultPointcutAdvisor(pointcut, springInterceptor);
    }

}
