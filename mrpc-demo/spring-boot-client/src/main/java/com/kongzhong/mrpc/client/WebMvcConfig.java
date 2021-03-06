package com.kongzhong.mrpc.client;

import com.kongzhong.mrpc.trace.config.TraceAutoConfigure;
import com.kongzhong.mrpc.trace.interceptor.TraceClientInterceptor;
import com.kongzhong.mrpc.trace.interceptor.TraceMvcInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * @author biezhi
 * @date 2017/11/29
 */
@Slf4j
@Configuration
public class WebMvcConfig extends WebMvcConfigurerAdapter {

    @Bean
    @ConditionalOnClass(TraceAutoConfigure.class)
    public TraceMvcInterceptor traceMvcInterceptor(@Autowired TraceClientInterceptor traceClientInterceptor){
        return new TraceMvcInterceptor(traceClientInterceptor.getTraceAutoConfigure());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        TraceMvcInterceptor bean = SimpleRpcClient.getBean(TraceMvcInterceptor.class);
        registry.addInterceptor(bean);
        log.info("添加 Trace 客户端拦截器");
    }
}
