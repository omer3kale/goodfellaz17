package com.goodfellaz17.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * TenantContext - ThreadLocal holder for current tenant information.
 * 
 * <p>In a multi-tenant architecture, each request operates in the context of a specific tenant.
 * This class provides thread-safe storage and retrieval of tenant information, ensuring that:
 * <ul>
 *   <li>Each request thread has isolated tenant context</li>
 *   <li>Database routing decisions use the correct tenant</li>
 *   <li>Audit trails capture the correct tenant identifier</li>
 * </ul>
 * 
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // In WebFilter or HandlerInterceptor:
 * TenantContext.setTenant(TenantInfo.of("goodfellaz17", "spotifybot"));
 * 
 * // In repository/service layer:
 * TenantInfo tenant = TenantContext.require();  // Throws if not set
 * String dbName = tenant.databaseName();
 * 
 * // After request completes:
 * TenantContext.clear();  // IMPORTANT: Prevent memory leaks
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>Uses {@link InheritableThreadLocal} to propagate tenant context to child threads
 * (e.g., async operations spawned from the request thread).
 * 
 * <h2>Security Note</h2>
 * <p>Always call {@link #clear()} after request processing to prevent:
 * <ul>
 *   <li>Memory leaks in thread pools</li>
 *   <li>Cross-tenant data leakage if thread is reused</li>
 * </ul>
 * 
 * @author RWTH Research Project
 * @see DatasourceRouter
 * @see TenantInfo
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    /**
     * ThreadLocal storage for tenant information.
     * Using InheritableThreadLocal so async operations inherit the tenant context.
     */
    private static final InheritableThreadLocal<TenantInfo> CURRENT_TENANT = new InheritableThreadLocal<>();

    // Private constructor - utility class
    private TenantContext() {
        throw new UnsupportedOperationException("TenantContext is a utility class");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Operations
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sets the current tenant for this thread.
     * 
     * @param tenant The tenant information (must not be null)
     * @throws IllegalArgumentException if tenant is null
     */
    public static void setTenant(TenantInfo tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }
        
        TenantInfo previous = CURRENT_TENANT.get();
        if (previous != null && !previous.equals(tenant)) {
            log.warn("TENANT_CONTEXT_OVERRIDE | previous={} | new={}", 
                    previous.tenantCode(), tenant.tenantCode());
        }
        
        CURRENT_TENANT.set(tenant);
        log.debug("TENANT_CONTEXT_SET | tenant={} | database={}", 
                tenant.tenantCode(), tenant.databaseName());
    }

    /**
     * Gets the current tenant, if set.
     * 
     * @return Optional containing tenant info, or empty if not set
     */
    public static Optional<TenantInfo> getTenant() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Gets the current tenant, throwing an exception if not set.
     * Use this in code paths where tenant context is mandatory.
     * 
     * @return The current tenant info
     * @throws TenantContextMissingException if no tenant is set
     */
    public static TenantInfo require() {
        TenantInfo tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new TenantContextMissingException(
                "Tenant context is required but not set. " +
                "Ensure TenantFilter or @RequiresTenant is configured."
            );
        }
        return tenant;
    }

    /**
     * Clears the tenant context for this thread.
     * MUST be called after request processing to prevent memory leaks.
     */
    public static void clear() {
        TenantInfo tenant = CURRENT_TENANT.get();
        if (tenant != null) {
            log.debug("TENANT_CONTEXT_CLEARED | tenant={}", tenant.tenantCode());
        }
        CURRENT_TENANT.remove();
    }

    /**
     * Checks if a tenant context is currently set.
     * 
     * @return true if tenant is set, false otherwise
     */
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Convenience Methods
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Gets the current tenant code, or "UNKNOWN" if not set.
     * Safe for logging without null checks.
     */
    public static String getTenantCodeOrUnknown() {
        TenantInfo tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant.tenantCode() : "UNKNOWN";
    }

    /**
     * Gets the current database name, or null if not set.
     */
    public static String getDatabaseName() {
        TenantInfo tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant.databaseName() : null;
    }

    /**
     * Executes a runnable within a specific tenant context, then restores the previous context.
     * 
     * @param tenant The tenant to use during execution
     * @param runnable The code to execute
     */
    public static void runAs(TenantInfo tenant, Runnable runnable) {
        TenantInfo previous = CURRENT_TENANT.get();
        try {
            setTenant(tenant);
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tenant Info Record
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Immutable holder for tenant information.
     * 
     * @param tenantCode Unique tenant identifier (e.g., "goodfellaz17")
     * @param databaseName Target database name (e.g., "spotifybot")
     * @param host Database host (default: localhost)
     * @param port Database port (default: 5432)
     * @param poolSize Connection pool size for this tenant
     */
    public record TenantInfo(
            String tenantCode,
            String databaseName,
            String host,
            int port,
            int poolSize
    ) {
        /**
         * Creates TenantInfo with default connection settings.
         */
        public static TenantInfo of(String tenantCode, String databaseName) {
            return new TenantInfo(tenantCode, databaseName, "localhost", 6432, 10);
        }

        /**
         * Creates TenantInfo for PgBouncer connection.
         */
        public static TenantInfo withPgBouncer(String tenantCode, String databaseName, int poolSize) {
            return new TenantInfo(tenantCode, databaseName, "localhost", 6432, poolSize);
        }

        /**
         * Builds the R2DBC connection URL for this tenant.
         */
        public String toR2dbcUrl() {
            return String.format("r2dbc:postgresql://%s:%d/%s", host, port, databaseName);
        }

        /**
         * Builds the JDBC connection URL for this tenant (for Flyway migrations).
         */
        public String toJdbcUrl() {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Exception
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Exception thrown when tenant context is required but not set.
     */
    public static class TenantContextMissingException extends RuntimeException {
        public TenantContextMissingException(String message) {
            super(message);
        }
    }
}
