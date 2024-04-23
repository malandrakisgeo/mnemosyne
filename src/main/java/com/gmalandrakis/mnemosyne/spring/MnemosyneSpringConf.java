package com.gmalandrakis.mnemosyne.spring;


import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.gmalandrakis.mnemosyne")
public class MnemosyneSpringConf {

    @Bean
    public Advisor advisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.gmalandrakis.mnemosyne.annotations.Cached)");
        return new DefaultPointcutAdvisor(pointcut, new SpringInterceptor(new MnemoService()));
    }


}
