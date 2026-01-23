-- Test Schema for Integration Tests
-- Combined schema (V1 + V2 fixes) for Testcontainers PostgreSQL

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- USERS TABLE
-- =============================================================================
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
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION', 'PENDING', 'BANNED')),
    CONSTRAINT users_balance_positive CHECK (balance >= 0)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_api_key ON users(api_key) WHERE api_key IS NOT NULL;
CREATE INDEX idx_users_tier_status ON users(tier, status);

-- =============================================================================
-- SERVICES TABLE
-- =============================================================================
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
);

CREATE INDEX idx_services_type_active ON services(service_type, is_active);

-- =============================================================================
-- ORDERS TABLE (with V2 fixes: service_name, price_per_unit, total_cost, remains)
-- =============================================================================
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL,
    service_id          UUID NOT NULL,
    service_name        VARCHAR(255),
    quantity            INTEGER NOT NULL,
    delivered           INTEGER NOT NULL DEFAULT 0,
    remains             INTEGER,
    target_url          VARCHAR(512) NOT NULL,
    geo_profile         VARCHAR(50) NOT NULL DEFAULT 'WORLDWIDE',
    speed_multiplier    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    price_per_unit      DECIMAL(10,4),
    total_cost          DECIMAL(10,2) NOT NULL,
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
    CONSTRAINT orders_geo_profile_check CHECK (geo_profile IN (
        'WORLDWIDE', 'USA', 'UK', 'DE', 'FR', 'BR', 'MX', 
        'LATAM', 'EUROPE', 'ASIA', 'PREMIUM_MIX',
        'US_FOCUSED', 'EU_FOCUSED', 'LATAM_FOCUSED', 'ASIA_FOCUSED'
    )),
    CONSTRAINT orders_status_check CHECK (status IN (
        'PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED', 
        'PARTIAL', 'FAILED', 'REFUNDED', 'CANCELLED'
    )),
    CONSTRAINT orders_speed_valid CHECK (speed_multiplier >= 0.1 AND speed_multiplier <= 5.0),
    CONSTRAINT orders_cost_positive CHECK (total_cost >= 0)
);

CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_status_created ON orders(status, created_at);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- =============================================================================
-- BALANCE TRANSACTIONS TABLE
-- =============================================================================
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
);

CREATE INDEX idx_balance_tx_user_time ON balance_transactions(user_id, timestamp DESC);
CREATE INDEX idx_balance_tx_order ON balance_transactions(order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_balance_tx_timestamp ON balance_transactions(timestamp);

-- =============================================================================
-- PROXY NODES TABLE (minimal for tests)
-- =============================================================================
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
    health_state            VARCHAR(20) NOT NULL DEFAULT 'HEALTHY',
    tags                    JSONB,
    
    CONSTRAINT proxy_nodes_ip_unique UNIQUE (public_ip),
    CONSTRAINT proxy_nodes_health_state_check CHECK (health_state IN ('HEALTHY', 'DEGRADED', 'OFFLINE'))
);

CREATE INDEX idx_proxy_nodes_health_selection ON proxy_nodes(tier, status, health_state, current_load) 
    WHERE status = 'ONLINE' AND health_state != 'OFFLINE';

-- =============================================================================
-- PROXY METRICS TABLE (minimal for tests)
-- =============================================================================
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
    CONSTRAINT proxy_metrics_node_unique UNIQUE (proxy_node_id)
);


-- =============================================================================
-- PREMIUM ACCOUNTS TABLE (for bot farm)
-- =============================================================================
CREATE TABLE premium_accounts (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email                   VARCHAR(100) NOT NULL,
    password                VARCHAR(100),
    cookies                 TEXT,
    spotify_refresh_token   TEXT,
    spotify_access_token    TEXT,
    region                  VARCHAR(20) DEFAULT 'WORLDWIDE',
    premium_expiry          DATE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_premium_accounts_expiry ON premium_accounts(premium_expiry);

-- =============================================================================
-- ORDER TASKS TABLE (for 15k delivery)
-- =============================================================================
CREATE TABLE order_tasks (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id              UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sequence_number       INTEGER NOT NULL,
    quantity              INTEGER NOT NULL CHECK (quantity > 0 AND quantity <= 1000),
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts              INTEGER NOT NULL DEFAULT 0,
    max_attempts          INTEGER NOT NULL DEFAULT 3,
    last_error            VARCHAR(1000),
    proxy_node_id         UUID REFERENCES proxy_nodes(id) ON DELETE SET NULL,
    execution_started_at  TIMESTAMP WITH TIME ZONE,
    executed_at           TIMESTAMP WITH TIME ZONE,
    scheduled_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_after           TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    idempotency_token     VARCHAR(128) NOT NULL,
    worker_id             VARCHAR(64),
    refunded              BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT uq_order_task_sequence UNIQUE (order_id, sequence_number),
    CONSTRAINT uq_task_idempotency_token UNIQUE (idempotency_token)
);

CREATE INDEX idx_order_tasks_order_status ON order_tasks (order_id, status);
CREATE INDEX idx_order_tasks_pending_scheduled ON order_tasks (scheduled_at ASC) WHERE status = 'PENDING';
CREATE INDEX idx_order_tasks_failed_permanent ON order_tasks (created_at DESC) WHERE status = 'FAILED_PERMANENT';

-- =============================================================================
-- Add estimated_completion_at and task delivery columns to orders
-- =============================================================================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS estimated_completion_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failed_permanent_plays INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS uses_task_delivery BOOLEAN NOT NULL DEFAULT FALSE;

-- =============================================================================
-- V9: REFUND HARDENING (Constraints + Audit Tables)
-- =============================================================================

-- Refund events audit table (append-only)
CREATE TABLE IF NOT EXISTS refund_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    task_id         UUID NOT NULL REFERENCES order_tasks(id) ON DELETE RESTRICT,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL,
    amount          NUMERIC(12,4) NOT NULL,
    price_per_unit  NUMERIC(12,8) NOT NULL,
    worker_id       VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_refund_event_task UNIQUE (task_id)
);

CREATE INDEX IF NOT EXISTS idx_refund_events_order ON refund_events (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_events_user ON refund_events (user_id);

-- Refund anomalies table (for reconciliation)
CREATE TABLE IF NOT EXISTS refund_anomalies (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id                UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    anomaly_type            VARCHAR(50) NOT NULL,
    expected_refund_amount  NUMERIC(12,4),
    actual_refund_amount    NUMERIC(12,4),
    expected_failed_plays   INTEGER,
    actual_failed_plays     INTEGER,
    refunded_task_count     INTEGER,
    severity                VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    resolved_at             TIMESTAMPTZ,
    resolution_notes        TEXT
);

-- Partial index for refund processing
CREATE INDEX IF NOT EXISTS idx_tasks_refund_pending 
    ON order_tasks (order_id, status, refunded)
    WHERE status = 'FAILED_PERMANENT' AND refunded = FALSE;
