package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.infrastructure.persistence.TenantContext.TenantInfo;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DatasourceRouter - Multi-tenant database connection routing.
 * 
 * <p>Implements the AbstractRoutingConnectionFactory pattern for R2DBC,
 * routing database connections based on the current {@link TenantContext}.
 * 
 * <h2>Architecture</h2>
 * <pre>
 * Request → TenantFilter → TenantContext.setTenant()
 *                              ↓
 *         Repository → DatasourceRouter.getConnection()
 *                              ↓
 *                   TenantContext.require().databaseName()
 *                              ↓
 *                   ConnectionPool for that tenant
 * </pre>
 * 
 * <h2>Connection Pooling</h2>
 * <p>Each tenant gets its own connection pool, lazily created on first access.
 * Pool configuration is fetched from the tenant_databases catalog table.
 * 
 * <h2>Security</h2>
 * <ul>
 *   <li>Tenant A cannot access Tenant B's connection pool</li>
 *   <li>Missing tenant context throws exception (fail-safe)</li>
 *   <li>Unknown tenant codes are rejected</li>
 * </ul>
 * 
 * <h2>Thesis Relevance</h2>
 * <p>"DDD Bounded Contexts are enforced at the infrastructure layer through
 *    tenant-aware connection routing, preventing cross-context data leakage."
 * 
 * @author RWTH Research Project
 * @see TenantContext
 */
@Component
public class DatasourceRouter {

    private static final Logger log = LoggerFactory.getLogger(DatasourceRouter.class);

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────────────────────

    @Value("${goodfellaz17.multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Value("${goodfellaz17.multitenancy.default-tenant:goodfellaz17}")
    private String defaultTenantCode;

    @Value("${spring.r2dbc.username:spotifybot_app}")
    private String dbUsername;

    @Value("${spring.r2dbc.password:#{null}}")
    private String dbPassword;

    // Default connection settings (via PgBouncer)
    @Value("${goodfellaz17.multitenancy.pgbouncer.host:localhost}")
    private String pgBouncerHost;

    @Value("${goodfellaz17.multitenancy.pgbouncer.port:6432}")
    private int pgBouncerPort;

    // ─────────────────────────────────────────────────────────────────────────────
    // Connection Pool Cache
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Tenant code → ConnectionPool mapping.
     * Pools are created lazily and cached for the lifetime of the application.
     */
    private final Map<String, ConnectionPool> connectionPools = new ConcurrentHashMap<>();

    /**
     * Tenant code → TenantInfo mapping (cached from database).
     */
    private final Map<String, TenantInfo> tenantRegistry = new ConcurrentHashMap<>();

    /**
     * Primary DatabaseClient for querying tenant_databases catalog.
     * This connects to the main database (goodfellaz17/spotifybot).
     */
    private final DatabaseClient catalogClient;

    public DatasourceRouter(DatabaseClient databaseClient) {
        this.catalogClient = databaseClient;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() {
        log.info("DATASOURCE_ROUTER_INIT | multitenancy={} | defaultTenant={}", 
                multitenancyEnabled, defaultTenantCode);

        if (multitenancyEnabled) {
            // Load tenant registry from database
            loadTenantRegistry();
        } else {
            // Single-tenant mode: register default tenant
            TenantInfo defaultTenant = TenantInfo.withPgBouncer(
                    defaultTenantCode, 
                    "spotifybot", 
                    15
            );
            tenantRegistry.put(defaultTenantCode, defaultTenant);
            log.info("SINGLE_TENANT_MODE | tenant={}", defaultTenantCode);
        }
    }

    /**
     * Loads all active tenants from the tenant_databases catalog.
     */
    private void loadTenantRegistry() {
        catalogClient.sql("""
                SELECT tenant_code, database_name, host, port, connection_pool_size
                FROM tenant_databases
                WHERE status = 'ACTIVE'
                """)
                .fetch()
                .all()
                .doOnNext(row -> {
                    String tenantCode = (String) row.get("tenant_code");
                    TenantInfo info = new TenantInfo(
                            tenantCode,
                            (String) row.get("database_name"),
                            (String) row.get("host"),
                            ((Number) row.get("port")).intValue(),
                            ((Number) row.get("connection_pool_size")).intValue()
                    );
                    tenantRegistry.put(tenantCode, info);
                    log.info("TENANT_REGISTERED | tenant={} | database={}", 
                            tenantCode, info.databaseName());
                })
                .doOnError(e -> log.error("TENANT_REGISTRY_LOAD_FAILED | error={}", e.getMessage()))
                .subscribe();
    }

    @PreDestroy
    public void shutdown() {
        log.info("DATASOURCE_ROUTER_SHUTDOWN | closing {} connection pools", connectionPools.size());
        connectionPools.values().forEach(pool -> {
            try {
                pool.dispose();
            } catch (Exception e) {
                log.warn("Failed to close connection pool: {}", e.getMessage());
            }
        });
        connectionPools.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Connection Routing
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Gets the ConnectionFactory for the current tenant.
     * 
     * <p>If multi-tenancy is disabled, returns the default tenant's connection.
     * If enabled, routes based on TenantContext.
     * 
     * @return ConnectionFactory for the current tenant
     * @throws TenantContext.TenantContextMissingException if multi-tenancy is enabled but no tenant is set
     * @throws UnknownTenantException if the tenant code is not registered
     */
    public ConnectionFactory getConnectionFactory() {
        String tenantCode = resolveTenantCode();
        return getOrCreateConnectionPool(tenantCode);
    }

    /**
     * Gets a DatabaseClient for the current tenant.
     * 
     * @return DatabaseClient routed to the current tenant's database
     */
    public DatabaseClient getDatabaseClient() {
        return DatabaseClient.builder()
                .connectionFactory(getConnectionFactory())
                .build();
    }

    /**
     * Gets a DatabaseClient for a specific tenant (admin operations).
     * 
     * @param tenantCode The tenant to connect to
     * @return DatabaseClient for the specified tenant
     */
    public DatabaseClient getDatabaseClient(String tenantCode) {
        validateTenantCode(tenantCode);
        return DatabaseClient.builder()
                .connectionFactory(getOrCreateConnectionPool(tenantCode))
                .build();
    }

    /**
     * Resolves the current tenant code from context or default.
     */
    private String resolveTenantCode() {
        if (!multitenancyEnabled) {
            return defaultTenantCode;
        }

        Optional<TenantInfo> tenant = TenantContext.getTenant();
        if (tenant.isEmpty()) {
            throw new TenantContext.TenantContextMissingException(
                "Multi-tenancy is enabled but no tenant context is set. " +
                "Ensure requests include X-Tenant-ID header or API key."
            );
        }

        return tenant.get().tenantCode();
    }

    /**
     * Validates that a tenant code is registered.
     */
    private void validateTenantCode(String tenantCode) {
        if (!tenantRegistry.containsKey(tenantCode)) {
            throw new UnknownTenantException("Unknown tenant: " + tenantCode);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Connection Pool Management
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Gets or creates a connection pool for the specified tenant.
     * 
     * <p>Pools are created lazily and cached. Thread-safe via ConcurrentHashMap.
     */
    private ConnectionPool getOrCreateConnectionPool(String tenantCode) {
        return connectionPools.computeIfAbsent(tenantCode, this::createConnectionPool);
    }

    /**
     * Creates a new connection pool for a tenant.
     */
    private ConnectionPool createConnectionPool(String tenantCode) {
        TenantInfo tenant = tenantRegistry.get(tenantCode);
        if (tenant == null) {
            throw new UnknownTenantException("Cannot create pool for unknown tenant: " + tenantCode);
        }

        log.info("CREATING_CONNECTION_POOL | tenant={} | database={} | host={}:{} | poolSize={}", 
                tenantCode, tenant.databaseName(), pgBouncerHost, pgBouncerPort, tenant.poolSize());

        // Create PostgreSQL connection factory
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(pgBouncerHost)
                        .port(pgBouncerPort)
                        .database(tenant.databaseName())
                        .username(dbUsername)
                        .password(dbPassword)
                        .build()
        );

        // Wrap in connection pool
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .name("pool-" + tenantCode)
                .initialSize(Math.min(5, tenant.poolSize()))
                .maxSize(tenant.poolSize())
                .maxIdleTime(Duration.ofMinutes(10))
                .maxCreateConnectionTime(Duration.ofSeconds(5))
                .validationQuery("SELECT 1")
                .build();

        ConnectionPool pool = new ConnectionPool(poolConfig);
        
        log.info("CONNECTION_POOL_CREATED | tenant={} | maxSize={}", tenantCode, tenant.poolSize());
        return pool;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tenant Validation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Validates that user ID belongs to the current tenant.
     * Prevents cross-tenant data access.
     * 
     * @param userId The user ID to validate
     * @return Mono that completes if valid, errors if invalid
     */
    public Mono<Void> validateUserBelongsToTenant(String userId) {
        if (!multitenancyEnabled) {
            return Mono.empty();
        }

        String tenantCode = TenantContext.getTenantCodeOrUnknown();
        
        return getDatabaseClient()
                .sql("SELECT 1 FROM users WHERE id = :userId")
                .bind("userId", userId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new CrossTenantAccessException(
                        "User " + userId + " not found in tenant " + tenantCode
                )))
                .then();
    }

    /**
     * Validates that an order belongs to the current tenant.
     */
    public Mono<Void> validateOrderBelongsToTenant(String orderId) {
        if (!multitenancyEnabled) {
            return Mono.empty();
        }

        String tenantCode = TenantContext.getTenantCodeOrUnknown();
        
        return getDatabaseClient()
                .sql("SELECT 1 FROM orders WHERE id = :orderId")
                .bind("orderId", orderId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new CrossTenantAccessException(
                        "Order " + orderId + " not found in tenant " + tenantCode
                )))
                .then();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Admin Operations
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new tenant and creates its connection pool.
     * 
     * @param tenantInfo The new tenant configuration
     */
    public void registerTenant(TenantInfo tenantInfo) {
        tenantRegistry.put(tenantInfo.tenantCode(), tenantInfo);
        log.info("TENANT_REGISTERED | tenant={} | database={}", 
                tenantInfo.tenantCode(), tenantInfo.databaseName());
    }

    /**
     * Removes a tenant and closes its connection pool.
     */
    public void unregisterTenant(String tenantCode) {
        tenantRegistry.remove(tenantCode);
        ConnectionPool pool = connectionPools.remove(tenantCode);
        if (pool != null) {
            pool.dispose();
            log.info("TENANT_UNREGISTERED | tenant={}", tenantCode);
        }
    }

    /**
     * Refreshes the tenant registry from the database.
     */
    public void refreshTenantRegistry() {
        log.info("TENANT_REGISTRY_REFRESH | starting");
        tenantRegistry.clear();
        loadTenantRegistry();
    }

    /**
     * Gets pool statistics for monitoring.
     */
    public Map<String, PoolStats> getPoolStatistics() {
        Map<String, PoolStats> stats = new ConcurrentHashMap<>();
        connectionPools.forEach((tenant, pool) -> {
            stats.put(tenant, new PoolStats(
                    pool.getMetrics().map(m -> m.acquiredSize()).orElse(0),
                    pool.getMetrics().map(m -> m.allocatedSize()).orElse(0),
                    pool.getMetrics().map(m -> m.pendingAcquireSize()).orElse(0)
            ));
        });
        return stats;
    }

    public record PoolStats(int acquired, int allocated, int pending) {}

    // ─────────────────────────────────────────────────────────────────────────────
    // Exceptions
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Thrown when attempting to access an unregistered tenant.
     */
    public static class UnknownTenantException extends RuntimeException {
        public UnknownTenantException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when attempting to access data from another tenant.
     */
    public static class CrossTenantAccessException extends RuntimeException {
        public CrossTenantAccessException(String message) {
            super(message);
        }
    }
}
