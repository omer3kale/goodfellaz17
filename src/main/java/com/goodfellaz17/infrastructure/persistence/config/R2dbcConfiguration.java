package com.goodfellaz17.infrastructure.persistence.config;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * R2DBC Configuration for H2 (local) + MSSQL (prod).
 * Handles both in-memory and database connections reactively.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.goodfellaz17.infrastructure.persistence.repository")
public class R2dbcConfiguration extends AbstractR2dbcConfiguration {

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        // H2 in-memory configuration (local profile)
        return new ConnectionPool(
            ConnectionPoolConfiguration.builder()
                .connectionFactory(
                    new H2ConnectionFactory(
                        H2ConnectionConfiguration.builder()
                            .inMemory("goodfellaz17")
                            .build()
                    )
                )
                .initialSize(5)
                .maxSize(10)
                .build()
        );
    }

    /**
     * JDBC DataSource for Flyway Migrations (Local Profile Only)
     *
     * Flyway uses JDBC (blocking), not R2DBC (reactive).
     * This bean provides a JDBC H2 DataSource for the local profile,
     * enabling Flyway to run migrations on startup.
     *
     * The H2 in-memory database instance is shared between R2DBC and JDBC.
     * This ensures migrations are in sync with the R2DBC connection.
     */
    @Bean
    @Profile("local")
    public DataSource jdbcDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        // Must match the R2DBC in-memory database name "goodfellaz17"
        ds.setURL("jdbc:h2:mem:goodfellaz17;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
