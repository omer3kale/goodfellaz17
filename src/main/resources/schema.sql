-- GoodFellaz17 Schema Initialization (Neon PostgreSQL)
-- =====================================================

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
    id BIGSERIAL,
    proxy_id VARCHAR(255),
    track_id VARCHAR(255),
    duration INTEGER,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50),
    error_message TEXT,
    PRIMARY KEY (id, completed_at)
) PARTITION BY RANGE (completed_at);

-- Default partition for demo month
CREATE TABLE IF NOT EXISTS stream_results_default PARTITION OF stream_results
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
