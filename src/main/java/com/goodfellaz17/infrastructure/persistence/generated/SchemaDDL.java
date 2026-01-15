package com.goodfellaz17.infrastructure.persistence.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * PostgreSQL DDL as Java String literals.
 * 
 * Use these for programmatic schema management, testing, or embedding.
 * For production, prefer Flyway migrations (V1__Initial_Schema.sql).
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public final class SchemaDDL {
    
    private SchemaDDL() {
        // Utility class - no instantiation
    }
    
    // =========================================================================
    // EXTENSION
    // =========================================================================
    
    public static final String CREATE_UUID_EXTENSION = """
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp"
        """;
    
    // =========================================================================
    // USERS TABLE
    // =========================================================================
    
    public static final String CREATE_USERS_TABLE = """
        CREATE TABLE users (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            email               VARCHAR(255) NOT NULL,
            password_hash       VARCHAR(255) NOT NULL,
            tier                VARCHAR(50) NOT NULL DEFAULT 'CONSUMER',
            balance             DECIMAL(12,2) NOT NULL DEFAULT 0.00,
            api_key             VARCHAR(64),
            webhook_url         VARCHAR(512),
            discord_webhook     VARCHAR(512),
            company_name        VARCHAR(255),
            referral_code       VARCHAR(32),
            referred_by         UUID,
            created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_login          TIMESTAMP WITH TIME ZONE,
            status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
            email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
            two_factor_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
            
            CONSTRAINT users_email_unique UNIQUE (email),
            CONSTRAINT users_api_key_unique UNIQUE (api_key),
            CONSTRAINT users_referral_code_unique UNIQUE (referral_code),
            CONSTRAINT users_tier_check CHECK (tier IN ('CONSUMER', 'RESELLER', 'AGENCY')),
            CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')),
            CONSTRAINT users_balance_positive CHECK (balance >= 0)
        )
        """;
    
    public static final String CREATE_USERS_INDEXES = """
        CREATE INDEX idx_users_email ON users(email);
        CREATE INDEX idx_users_api_key ON users(api_key) WHERE api_key IS NOT NULL;
        CREATE INDEX idx_users_tier_status ON users(tier, status);
        CREATE INDEX idx_users_referred_by ON users(referred_by) WHERE referred_by IS NOT NULL
        """;
    
    public static final String DROP_USERS_TABLE = "DROP TABLE IF EXISTS users CASCADE";
    
    // =========================================================================
    // SERVICES TABLE
    // =========================================================================
    
    public static final String CREATE_SERVICES_TABLE = """
        CREATE TABLE services (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name                VARCHAR(100) NOT NULL,
            display_name        VARCHAR(255) NOT NULL,
            service_type        VARCHAR(50) NOT NULL,
            description         VARCHAR(1000),
            cost_per_1k         DECIMAL(8,2) NOT NULL,
            reseller_cost_per_1k DECIMAL(8,2) NOT NULL,
            agency_cost_per_1k  DECIMAL(8,2) NOT NULL,
            min_quantity        INTEGER NOT NULL DEFAULT 100,
            max_quantity        INTEGER NOT NULL DEFAULT 1000000,
            estimated_days_min  INTEGER NOT NULL DEFAULT 1,
            estimated_days_max  INTEGER NOT NULL DEFAULT 7,
            geo_profiles        JSONB NOT NULL DEFAULT '["WORLDWIDE"]',
            is_active           BOOLEAN NOT NULL DEFAULT TRUE,
            sort_order          INTEGER NOT NULL DEFAULT 0,
            created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            CONSTRAINT services_name_unique UNIQUE (name),
            CONSTRAINT services_type_check CHECK (service_type IN (
                'PLAYS', 'MONTHLY_LISTENERS', 'SAVES', 'FOLLOWS', 
                'PLAYLIST_FOLLOWERS', 'PLAYLIST_PLAYS'
            )),
            CONSTRAINT services_cost_positive CHECK (cost_per_1k > 0),
            CONSTRAINT services_quantity_valid CHECK (min_quantity > 0 AND max_quantity >= min_quantity)
        )
        """;
    
    public static final String CREATE_SERVICES_INDEXES = """
        CREATE INDEX idx_services_type_active ON services(service_type, is_active);
        CREATE INDEX idx_services_sort ON services(sort_order)
        """;
    
    public static final String DROP_SERVICES_TABLE = "DROP TABLE IF EXISTS services CASCADE";
    
    // =========================================================================
    // ORDERS TABLE
    // =========================================================================
    
    public static final String CREATE_ORDERS_TABLE = """
        CREATE TABLE orders (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            user_id             UUID NOT NULL,
            service_id          UUID NOT NULL,
            quantity            INTEGER NOT NULL,
            delivered           INTEGER NOT NULL DEFAULT 0,
            target_url          VARCHAR(512) NOT NULL,
            geo_profile         VARCHAR(50) NOT NULL DEFAULT 'WORLDWIDE',
            speed_multiplier    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
            status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
            cost                DECIMAL(10,2) NOT NULL,
            refund_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            start_count         INTEGER,
            current_count       INTEGER,
            created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            started_at          TIMESTAMP WITH TIME ZONE,
            completed_at        TIMESTAMP WITH TIME ZONE,
            failure_reason      VARCHAR(500),
            internal_notes      VARCHAR(1000),
            external_order_id   VARCHAR(64),
            webhook_delivered   BOOLEAN NOT NULL DEFAULT FALSE,
            
            CONSTRAINT orders_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            CONSTRAINT orders_service_fk FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT,
            CONSTRAINT orders_quantity_positive CHECK (quantity > 0),
            CONSTRAINT orders_delivered_valid CHECK (delivered >= 0 AND delivered <= quantity),
            CONSTRAINT orders_status_check CHECK (status IN (
                'PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED', 
                'PARTIAL', 'FAILED', 'REFUNDED', 'CANCELLED'
            )),
            CONSTRAINT orders_speed_valid CHECK (speed_multiplier >= 0.1 AND speed_multiplier <= 5.0),
            CONSTRAINT orders_cost_positive CHECK (cost >= 0)
        )
        """;
    
    public static final String CREATE_ORDERS_INDEXES = """
        CREATE INDEX idx_orders_user_status ON orders(user_id, status);
        CREATE INDEX idx_orders_status_created ON orders(status, created_at);
        CREATE INDEX idx_orders_target_url ON orders(target_url);
        CREATE INDEX idx_orders_external_id ON orders(external_order_id) WHERE external_order_id IS NOT NULL;
        CREATE INDEX idx_orders_pending_running ON orders(status) WHERE status IN ('PENDING', 'RUNNING')
        """;
    
    public static final String DROP_ORDERS_TABLE = "DROP TABLE IF EXISTS orders CASCADE";
    
    // =========================================================================
    // BALANCE TRANSACTIONS TABLE
    // =========================================================================
    
    public static final String CREATE_BALANCE_TRANSACTIONS_TABLE = """
        CREATE TABLE balance_transactions (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            user_id             UUID NOT NULL,
            order_id            UUID,
            amount              DECIMAL(10,2) NOT NULL,
            balance_before      DECIMAL(12,2) NOT NULL,
            balance_after       DECIMAL(12,2) NOT NULL,
            type                VARCHAR(50) NOT NULL,
            reason              VARCHAR(500) NOT NULL,
            payment_provider    VARCHAR(50),
            external_tx_id      VARCHAR(128),
            timestamp           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            CONSTRAINT balance_tx_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            CONSTRAINT balance_tx_order_fk FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
            CONSTRAINT balance_tx_type_check CHECK (type IN (
                'DEBIT', 'CREDIT', 'REFUND', 'BONUS', 'ADJUSTMENT'
            ))
        )
        """;
    
    public static final String CREATE_BALANCE_TRANSACTIONS_INDEXES = """
        CREATE INDEX idx_balance_tx_user_time ON balance_transactions(user_id, timestamp DESC);
        CREATE INDEX idx_balance_tx_type ON balance_transactions(type);
        CREATE INDEX idx_balance_tx_order ON balance_transactions(order_id) WHERE order_id IS NOT NULL
        """;
    
    public static final String DROP_BALANCE_TRANSACTIONS_TABLE = "DROP TABLE IF EXISTS balance_transactions CASCADE";
    
    // =========================================================================
    // PROXY NODES TABLE
    // =========================================================================
    
    public static final String CREATE_PROXY_NODES_TABLE = """
        CREATE TABLE proxy_nodes (
            id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            provider                VARCHAR(50) NOT NULL,
            provider_instance_id    VARCHAR(64),
            public_ip               VARCHAR(45) NOT NULL,
            port                    INTEGER NOT NULL,
            region                  VARCHAR(50) NOT NULL,
            country                 VARCHAR(2) NOT NULL,
            city                    VARCHAR(100),
            tier                    VARCHAR(50) NOT NULL,
            capacity                INTEGER NOT NULL DEFAULT 100,
            current_load            INTEGER NOT NULL DEFAULT 0,
            cost_per_hour           DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
            auth_username           VARCHAR(64),
            auth_password           VARCHAR(128),
            registered_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_healthcheck        TIMESTAMP WITH TIME ZONE,
            status                  VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
            tags                    JSONB,
            
            CONSTRAINT proxy_nodes_ip_unique UNIQUE (public_ip),
            CONSTRAINT proxy_nodes_provider_check CHECK (provider IN (
                'VULTR', 'HETZNER', 'NETCUP', 'CONTABO', 'OVH', 
                'AWS', 'DIGITALOCEAN', 'LINODE'
            )),
            CONSTRAINT proxy_nodes_tier_check CHECK (tier IN (
                'DATACENTER', 'ISP', 'TOR', 'RESIDENTIAL', 'MOBILE'
            )),
            CONSTRAINT proxy_nodes_status_check CHECK (status IN (
                'ONLINE', 'OFFLINE', 'MAINTENANCE', 'BANNED', 'RATE_LIMITED'
            )),
            CONSTRAINT proxy_nodes_port_valid CHECK (port >= 1 AND port <= 65535),
            CONSTRAINT proxy_nodes_capacity_positive CHECK (capacity > 0),
            CONSTRAINT proxy_nodes_load_valid CHECK (current_load >= 0)
        )
        """;
    
    public static final String CREATE_PROXY_NODES_INDEXES = """
        CREATE INDEX idx_proxy_nodes_tier_status ON proxy_nodes(tier, status);
        CREATE INDEX idx_proxy_nodes_region_tier ON proxy_nodes(region, tier);
        CREATE INDEX idx_proxy_nodes_provider ON proxy_nodes(provider);
        CREATE INDEX idx_proxy_nodes_country_tier ON proxy_nodes(country, tier);
        CREATE INDEX idx_proxy_nodes_available ON proxy_nodes(tier, status, current_load) WHERE status = 'ONLINE'
        """;
    
    public static final String DROP_PROXY_NODES_TABLE = "DROP TABLE IF EXISTS proxy_nodes CASCADE";
    
    // =========================================================================
    // PROXY METRICS TABLE
    // =========================================================================
    
    public static final String CREATE_PROXY_METRICS_TABLE = """
        CREATE TABLE proxy_metrics (
            id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            proxy_node_id           UUID NOT NULL,
            total_requests          BIGINT NOT NULL DEFAULT 0,
            successful_requests     BIGINT NOT NULL DEFAULT 0,
            failed_requests         BIGINT NOT NULL DEFAULT 0,
            success_rate            DOUBLE PRECISION NOT NULL DEFAULT 1.0,
            ban_rate                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            latency_p50             INTEGER NOT NULL DEFAULT 0,
            latency_p95             INTEGER NOT NULL DEFAULT 0,
            latency_p99             INTEGER NOT NULL DEFAULT 0,
            active_connections      INTEGER NOT NULL DEFAULT 0,
            peak_connections        INTEGER NOT NULL DEFAULT 0,
            error_codes             JSONB,
            bytes_transferred       BIGINT NOT NULL DEFAULT 0,
            estimated_cost          DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
            last_updated            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            window_start            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            CONSTRAINT proxy_metrics_node_fk FOREIGN KEY (proxy_node_id) REFERENCES proxy_nodes(id) ON DELETE CASCADE,
            CONSTRAINT proxy_metrics_node_unique UNIQUE (proxy_node_id),
            CONSTRAINT proxy_metrics_rates_valid CHECK (
                success_rate >= 0.0 AND success_rate <= 1.0 AND
                ban_rate >= 0.0 AND ban_rate <= 1.0
            )
        )
        """;
    
    public static final String CREATE_PROXY_METRICS_INDEXES = """
        CREATE INDEX idx_proxy_metrics_node ON proxy_metrics(proxy_node_id);
        CREATE INDEX idx_proxy_metrics_success_rate ON proxy_metrics(success_rate DESC);
        CREATE INDEX idx_proxy_metrics_last_updated ON proxy_metrics(last_updated)
        """;
    
    public static final String DROP_PROXY_METRICS_TABLE = "DROP TABLE IF EXISTS proxy_metrics CASCADE";
    
    // =========================================================================
    // DEVICE NODES TABLE
    // =========================================================================
    
    public static final String CREATE_DEVICE_NODES_TABLE = """
        CREATE TABLE device_nodes (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            device_type         VARCHAR(50) NOT NULL,
            os_version          VARCHAR(50) NOT NULL,
            device_model        VARCHAR(100),
            app_version         VARCHAR(20),
            location            VARCHAR(100) NOT NULL,
            country             VARCHAR(2) NOT NULL,
            timezone            VARCHAR(50) NOT NULL,
            capacity            INTEGER NOT NULL DEFAULT 10,
            active_sessions     INTEGER NOT NULL DEFAULT 0,
            last_heartbeat      TIMESTAMP WITH TIME ZONE,
            status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
            fingerprint         VARCHAR(500),
            registered_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            retired_at          TIMESTAMP WITH TIME ZONE,
            
            CONSTRAINT device_nodes_type_check CHECK (device_type IN (
                'PHONE_ANDROID', 'PHONE_IOS', 'EMULATOR_ANDROID', 
                'EMULATOR_IOS', 'DESKTOP_WEB', 'MOBILE_WEB'
            )),
            CONSTRAINT device_nodes_status_check CHECK (status IN (
                'ACTIVE', 'IDLE', 'MAINTENANCE', 'RETIRED'
            ))
        )
        """;
    
    public static final String CREATE_DEVICE_NODES_INDEXES = """
        CREATE INDEX idx_device_nodes_type_status ON device_nodes(device_type, status);
        CREATE INDEX idx_device_nodes_country ON device_nodes(country);
        CREATE INDEX idx_device_nodes_available ON device_nodes(device_type, status, active_sessions) WHERE status = 'ACTIVE'
        """;
    
    public static final String DROP_DEVICE_NODES_TABLE = "DROP TABLE IF EXISTS device_nodes CASCADE";
    
    // =========================================================================
    // TOR CIRCUITS TABLE
    // =========================================================================
    
    public static final String CREATE_TOR_CIRCUITS_TABLE = """
        CREATE TABLE tor_circuits (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            circuit_id          VARCHAR(64) NOT NULL,
            entry_node          VARCHAR(100) NOT NULL,
            middle_node         VARCHAR(100),
            exit_node           VARCHAR(100) NOT NULL,
            exit_geo            VARCHAR(2) NOT NULL,
            success_rate        DOUBLE PRECISION NOT NULL DEFAULT 1.0,
            latency_p95         INTEGER NOT NULL DEFAULT 0,
            request_count       BIGINT NOT NULL DEFAULT 0,
            created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
            retired_at          TIMESTAMP WITH TIME ZONE,
            status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
            
            CONSTRAINT tor_circuits_id_unique UNIQUE (circuit_id),
            CONSTRAINT tor_circuits_status_check CHECK (status IN (
                'ACTIVE', 'RETIRED', 'EXPIRED', 'BLOCKED'
            )),
            CONSTRAINT tor_circuits_success_rate_valid CHECK (
                success_rate >= 0.0 AND success_rate <= 1.0
            )
        )
        """;
    
    public static final String CREATE_TOR_CIRCUITS_INDEXES = """
        CREATE INDEX idx_tor_circuits_status_geo ON tor_circuits(status, exit_geo);
        CREATE INDEX idx_tor_circuits_expires ON tor_circuits(expires_at);
        CREATE INDEX idx_tor_circuits_active ON tor_circuits(status, success_rate DESC) WHERE status = 'ACTIVE'
        """;
    
    public static final String DROP_TOR_CIRCUITS_TABLE = "DROP TABLE IF EXISTS tor_circuits CASCADE";
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Get full DDL for all tables in creation order.
     */
    public static String getFullCreateDDL() {
        return String.join(";\n\n",
            CREATE_UUID_EXTENSION,
            CREATE_USERS_TABLE,
            CREATE_USERS_INDEXES,
            CREATE_SERVICES_TABLE,
            CREATE_SERVICES_INDEXES,
            CREATE_ORDERS_TABLE,
            CREATE_ORDERS_INDEXES,
            CREATE_BALANCE_TRANSACTIONS_TABLE,
            CREATE_BALANCE_TRANSACTIONS_INDEXES,
            CREATE_PROXY_NODES_TABLE,
            CREATE_PROXY_NODES_INDEXES,
            CREATE_PROXY_METRICS_TABLE,
            CREATE_PROXY_METRICS_INDEXES,
            CREATE_DEVICE_NODES_TABLE,
            CREATE_DEVICE_NODES_INDEXES,
            CREATE_TOR_CIRCUITS_TABLE,
            CREATE_TOR_CIRCUITS_INDEXES
        );
    }
    
    /**
     * Get full DDL for dropping all tables in reverse order.
     */
    public static String getFullDropDDL() {
        return String.join(";\n",
            DROP_TOR_CIRCUITS_TABLE,
            DROP_DEVICE_NODES_TABLE,
            DROP_PROXY_METRICS_TABLE,
            DROP_PROXY_NODES_TABLE,
            DROP_BALANCE_TRANSACTIONS_TABLE,
            DROP_ORDERS_TABLE,
            DROP_SERVICES_TABLE,
            DROP_USERS_TABLE
        );
    }
}
