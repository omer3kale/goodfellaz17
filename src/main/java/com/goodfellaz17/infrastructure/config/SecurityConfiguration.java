package com.goodfellaz17.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * SecurityConfiguration — Minimal security for Slice A (WebFlux/Reactive).
 * Allows public access to /api/orders and /actuator/health for testing.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(authz -> authz
                .pathMatchers("/api/orders/**").permitAll()
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .httpBasic(spec -> {})
            .csrf().disable();
        
        return http.build();
    }
}

