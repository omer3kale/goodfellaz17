package com.goodfellaz17.infrastructure.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import javax.sql.DataSource;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * Manual R2DBC Configuration.
 *
 * This explicitly creates the R2DBC beans since auto-configuration
 * is not detecting them properly in the Spring Boot run context.
 */
@Configuration
public class R2dbcConfig {

    @Value("${spring.r2dbc.url:r2dbc:mssql://localhost:1433;database=goodfellaz17}")
    private String r2dbcUrl;

    @Value("${spring.r2dbc.username:sa}")
    private String username;

    @Value("${spring.r2dbc.password:YourStrong@Passw0rd}")
    private String password;

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        // Parse the R2DBC URL
        String url = r2dbcUrl.replace("r2dbc:", "");

        // Support H2, PostgreSQL, and MSSQL
        if (url.startsWith("h2:")) {
            // H2 in-memory: h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
            String h2Url = "r2dbc:h2:mem:///testdb?DB_CLOSE_DELAY=-1&DB_CLOSE_ON_EXIT=FALSE";
            return ConnectionFactories.get(h2Url);
        }

        if (url.startsWith("mssql://")) {
            // MSSQL: mssql://host:port;database=dbname
            // Parse: mssql://localhost:1433;database=goodfellaz17
            String mssqlPart = url.replace("mssql://", "");
            
            String[] parts = mssqlPart.split(";database=");
            if (parts.length < 2) {
                // Fallback to default ConnectionFactories
                return ConnectionFactories.get(r2dbcUrl);
            }

            String hostPort = parts[0]; // localhost:1433
            String database = parts[1]; // goodfellaz17

            String[] hp = hostPort.split(":");
            String host = hp[0];
            int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 1433;

            ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                    .option(DRIVER, "mssql")
                    .option(HOST, host)
                    .option(PORT, port)
                    .option(DATABASE, database)
                    .option(USER, username)
                    .option(PASSWORD, password)
                    .build();

            return ConnectionFactories.get(options);
        }

        // PostgreSQL URL parsing (legacy support): postgresql://localhost:5432/goodfellaz17
        if (url.startsWith("postgresql://")) {
            String pgUrl = url.replace("postgresql://", "");
            String[] hostDb = pgUrl.split("/");
            if (hostDb.length < 2) {
                return ConnectionFactories.get(r2dbcUrl);
            }

            String hostPort = hostDb[0];
            String database = hostDb[1];

            String[] hp = hostPort.split(":");
            String host = hp[0];
            int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 5432;

            ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                    .option(DRIVER, "postgresql")
                    .option(HOST, host)
                    .option(PORT, port)
                    .option(DATABASE, database)
                    .option(USER, username)
                    .option(PASSWORD, password)
                    .build();

            return ConnectionFactories.get(options);
        }

        // Default fallback
        return ConnectionFactories.get(r2dbcUrl);
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
