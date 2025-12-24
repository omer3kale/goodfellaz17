package com.goodfellaz17.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Infrastructure Config - Async bot executor thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "botExecutor")
    public Executor botExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("bot-");
        executor.initialize();
        return executor;
    }
}
