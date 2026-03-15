package com.goodfellaz17.infrastructure.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway Configuration for H2 In-Memory Database
 *
 * Flyway normally uses JDBC DataSource (auto-configured by Spring Boot).
 * For H2 in-memory via R2DBC, we need to explicitly initialize Flyway with
 * a JDBC H2 DataSource to ensure migrations run before the app starts.
 *
 * This bean only activates with:
 * - @Profile("local") → Uses H2 in-memory
 * - Flyway is disabled in test profile explicitly
 * - Production uses MSSQL with auto-configured Flyway
 */
@Configuration
@Profile("local")
public class FlywayConfig {

    @Value("${spring.r2dbc.url:r2dbc:h2:mem:testdb}")
    private String r2dbcUrl;

    /**
     * Flyway initialization bean (disabled for H2).
     *
     * This bean is intentionally a no-op when using H2 in-memory database
     * because the migration files (V1-V19) are PostgreSQL-specific and cannot
     * run on H2.
     *
     * For local development with H2:
     * - Flyway is disabled in application-local.yml (spring.flyway.enabled=false)
     * - Instead, use the default schema.sql from src/main/resources/schema.sql
     *
     * For production with real database (PostgreSQL):
     * - Flyway will auto-run via Spring Boot auto-configuration
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        // H2 in-memory: Skip Flyway (migrations are PostgreSQL-specific)
        // This bean is optional and only runs if spring.flyway.enabled=true
        return null;
    }
}
