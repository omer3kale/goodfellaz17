package com.goodfellaz17.infrastructure.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * Manual R2DBC Configuration.
 *
 * This explicitly creates the R2DBC beans since auto-configuration
 * is not detecting them properly in the Spring Boot run context.
 */
@Configuration
public class R2dbcConfig {

    @Value("${spring.r2dbc.url:r2dbc:postgresql://localhost:5432/goodfellaz17}")
    private String r2dbcUrl;

    @Value("${spring.r2dbc.username:goodfellaz17}")
    private String username;

    @Value("${spring.r2dbc.password:localdev123}")
    private String password;

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        // Parse the R2DBC URL
        String url = r2dbcUrl.replace("r2dbc:", "");

        // Support both PostgreSQL and H2 in-memory databases
        if (url.startsWith("h2:")) {
            // H2 in-memory: h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
            // Convert H2 format to valid R2DBC format
            String h2Url = "r2dbc:h2:mem:///testdb?DB_CLOSE_DELAY=-1&DB_CLOSE_ON_EXIT=FALSE";
            return ConnectionFactories.get(h2Url);
        }

        // PostgreSQL URL parsing: postgresql://localhost:5432/goodfellaz17
        String[] parts = url.split("://");
        if (parts.length < 2) {
            // Fallback to default ConnectionFactories
            return ConnectionFactories.get(r2dbcUrl);
        }

        String driver = parts[0]; // postgresql
        String rest = parts[1]; // localhost:5432/goodfellaz17

        String[] hostDb = rest.split("/");
        if (hostDb.length < 2) {
            return ConnectionFactories.get(r2dbcUrl);
        }

        String hostPort = hostDb[0]; // localhost:5432
        String database = hostDb[1]; // goodfellaz17

        String[] hp = hostPort.split(":");
        String host = hp[0];
        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 5432;

        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(DRIVER, driver)
                .option(HOST, host)
                .option(PORT, port)
                .option(DATABASE, database)
                .option(USER, username)
                .option(PASSWORD, password)
                .build();

        return ConnectionFactories.get(options);
    }

    @Bean
    @Primary
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.builder()
                .connectionFactory(connectionFactory)
                .build();
    }

    @Bean
    @Primary
    public R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
