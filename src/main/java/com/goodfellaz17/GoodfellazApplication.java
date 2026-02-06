package com.goodfellaz17;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

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
@ComponentScan(
    basePackages = "com.goodfellaz17",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.goodfellaz17\\.account\\..*"
    )
)
@EnableAsync
@EnableScheduling
@EnableR2dbcRepositories(basePackages = {"com.goodfellaz17.infrastructure.persistence", "com.goodfellaz17.account.service", "com.goodfellaz17.order.repository"})
public class GoodfellazApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodfellazApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
