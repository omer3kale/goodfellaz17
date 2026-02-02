package com.goodfellaz17.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate bean.
 * Provides centralized RestTemplate bean to avoid circular dependencies.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates and configures RestTemplate bean for HTTP requests
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
