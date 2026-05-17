package com.example.evimind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Bean(name = "analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AnalysisTask-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 为 Spring MVC 的异步请求处理（SSE 流式接口等）配置自定义线程池，
     * 避免默认 SimpleAsyncTaskExecutor 每次请求新建线程造成泄漏风险。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor mvcAsync = new ThreadPoolTaskExecutor();
        mvcAsync.setCorePoolSize(5);
        mvcAsync.setMaxPoolSize(20);
        mvcAsync.setQueueCapacity(50);
        mvcAsync.setThreadNamePrefix("MvcAsync-");
        mvcAsync.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        mvcAsync.initialize();
        configurer.setTaskExecutor(mvcAsync);
        configurer.setDefaultTimeout(120_000); // 120s for SSE streams
    }
}
