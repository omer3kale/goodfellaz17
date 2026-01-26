package com.goodfellaz17;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.context.annotation.Bean;

/**
 * GOODFELLAZ17 Provider - Spring Boot 3.5 Application.
 *
 * SMM Panel API v2 implementation for streaming automation.
 *
 * Architecture: Hexagonal / Ports & Adapters
 * - Presentation → REST API (Perfect Panel v2 spec)
 * - Application → Use Cases + Services
 * - Domain → Entities + Business Rules
 * - Infrastructure → Python Stealth + Proxies + Supabase
 *
 * @author RWTH Research Project
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableR2dbcRepositories
public class GoodfellazApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodfellazApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
