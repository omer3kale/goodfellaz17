package com.goodfellaz17;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * GOODFELLAZ17 Provider - Spring Boot 3.5 Application.
 *
 * DDD delivery engine with reactive R2DBC persistence.
 *
 * Architecture: Hexagonal / Ports & Adapters w/ DDD
 * - Presentation → REST API (minimal)
 * - Application → Services + Use Cases
 * - Domain → Order, OrderTask, ExecutionResult + invariants
 * - Infrastructure → R2DBC (H2 local, MSSQL prod) + Repositories
 *
 * @author Goodfellaz17 Team
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GoodfellazApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodfellazApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}

