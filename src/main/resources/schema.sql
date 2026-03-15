-- GoodFellaz17 Schema Initialization (Neon PostgreSQL + H2 Local Dev)
-- ===================================================================

-- pipeline_spotify_accounts table for Phase 2A account provisioning
CREATE TABLE IF NOT EXISTS pipeline_spotify_accounts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    spotify_user_id VARCHAR(255),
    status VARCHAR(50) DEFAULT 'CREATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_played_at TIMESTAMP
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_spotify_status ON pipeline_spotify_accounts(status);
CREATE INDEX IF NOT EXISTS idx_spotify_email ON pipeline_spotify_accounts(email);
CREATE INDEX IF NOT EXISTS idx_spotify_created_at ON pipeline_spotify_accounts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_spotify_user_id ON pipeline_spotify_accounts(spotify_user_id);

-- Logging table for account creation events
CREATE TABLE IF NOT EXISTS account_creation_logs (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_logs_email ON account_creation_logs(email);
CREATE INDEX IF NOT EXISTS idx_account_logs_created ON account_creation_logs(created_at DESC);

-- Account creation metrics
CREATE TABLE IF NOT EXISTS account_creation_metrics (
    id BIGSERIAL PRIMARY KEY,
    total_created BIGINT DEFAULT 0,
    total_failed BIGINT DEFAULT 0,
    total_verified BIGINT DEFAULT 0,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Performance summary
CREATE TABLE IF NOT EXISTS performance_summary (
    id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(100),
    metric_value NUMERIC,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performance_metric ON performance_summary(metric_name, recorded_at DESC);

-- Stream Results for Thesis Evaluation
CREATE TABLE IF NOT EXISTS stream_results (
    id BIGSERIAL PRIMARY KEY,
    proxy_id VARCHAR(255),
    track_id VARCHAR(255),
    duration INTEGER,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50),
    error_message TEXT
);

-- ============================================================================
-- PHASE 1: ORDER DELIVERY SYSTEM (V15-V19 frozen schema)
-- ============================================================================
-- Tables created for local H2 development + production MSSQL compatibility

-- Orders table: Main aggregate root for delivery pipeline
-- Columns aligned with OrderEntity.java (24 fields)
CREATE TABLE IF NOT EXISTS orders (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    service_id CHAR(36) NOT NULL,
    created_by_api_key_id CHAR(36),
    track_id VARCHAR(255),
    artist_id VARCHAR(255),
    quantity INTEGER NOT NULL DEFAULT 0,
    price_tier_id CHAR(36),
    estimated_cost NUMERIC(19,4),
    actual_cost NUMERIC(19,4),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_changed_at TIMESTAMP,
    plays_delivered INTEGER NOT NULL DEFAULT 0,
    plays_failed INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_tenant ON orders(tenant_id);
CREATE INDEX IF NOT EXISTS idx_orders_service_id ON orders(service_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);

-- Order Tasks table: Decomposed units of work from order
-- Columns aligned with OrderTaskEntity.java (17 fields)
CREATE TABLE IF NOT EXISTS order_tasks (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    account_id CHAR(36),
    proxy_node_id CHAR(36),
    position INTEGER NOT NULL,
    track_id VARCHAR(255) NOT NULL,
    artist_id VARCHAR(255),
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    stale_after TIMESTAMP,
    last_error TEXT,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX IF NOT EXISTS idx_order_tasks_status ON order_tasks(status);
CREATE INDEX IF NOT EXISTS idx_order_tasks_order_id ON order_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_order_tasks_tenant_id ON order_tasks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_order_tasks_idempotency ON order_tasks(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_order_tasks_created_at ON order_tasks(created_at DESC);

-- Execution Results table: Detailed outcome of each task execution attempt
-- Columns aligned with ExecutionResultEntity.java (18 fields)
CREATE TABLE IF NOT EXISTS execution_results (
    id CHAR(36) PRIMARY KEY,
    order_task_id CHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    account_id CHAR(36),
    proxy_node_id CHAR(36),
    attempt_num INTEGER NOT NULL DEFAULT 1,
    success_flag BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(255),
    http_status INTEGER,
    executing_node_id VARCHAR(255),
    duration_ms BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detection_signal VARCHAR(255),
    detection_score NUMERIC(19,6),
    revenue_impact NUMERIC(19,4),
    FOREIGN KEY (order_task_id) REFERENCES order_tasks(id),
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX IF NOT EXISTS idx_execution_results_order_id ON execution_results(order_id);
CREATE INDEX IF NOT EXISTS idx_execution_results_task_id ON execution_results(order_task_id);
CREATE INDEX IF NOT EXISTS idx_execution_results_completed ON execution_results(completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_execution_results_tenant_id ON execution_results(tenant_id);
CREATE INDEX IF NOT EXISTS idx_execution_results_success ON execution_results(success_flag);
