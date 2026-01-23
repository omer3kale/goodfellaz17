-- =============================================================================
-- Flyway Migration: V12__Multi_Tenant_Catalog.sql
-- 
-- Purpose: Multi-tenant infrastructure for isolating SMM panel databases
-- 
-- Architecture:
--   - Each tenant (goodfellaz17, botzzz773, etc.) gets its own database
--   - tenant_databases: Central catalog of all tenant DBs
--   - Enables: isolated backups, per-tenant quotas, schema versioning
--
-- Thesis Relevance:
--   "DDD Bounded Contexts map naturally to tenant databases, proving
--    that self-hosted infrastructure can scale to multiple clients
--    while maintaining strict data isolation."
--
-- @author RWTH Research Project
-- @version 1.0.0
-- =============================================================================

-- =============================================================================
-- TENANT DATABASES CATALOG
-- =============================================================================
-- Master registry of all tenant databases in the platform.
-- Used by DatasourceRouter to resolve tenant → database mapping.
-- =============================================================================
CREATE TABLE tenant_databases (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Tenant Identity
    tenant_code             VARCHAR(50) NOT NULL,       -- e.g., 'goodfellaz17', 'botzzz773'
    database_name           VARCHAR(100) NOT NULL,      -- e.g., 'spotifybot', 'botzzz773_db'
    display_name            VARCHAR(255) NOT NULL,      -- e.g., 'Goodfellaz17 Production'
    
    -- Ownership
    owner_id                UUID,                       -- FK to users.id (tenant admin)
    owner_email             VARCHAR(255) NOT NULL,      -- Contact email
    
    -- Connection Details (for DatasourceRouter)
    host                    VARCHAR(255) NOT NULL DEFAULT 'localhost',
    port                    INTEGER NOT NULL DEFAULT 5432,
    connection_pool_size    INTEGER NOT NULL DEFAULT 10,
    
    -- Schema Versioning
    current_schema_version  VARCHAR(20) NOT NULL DEFAULT 'V1',
    target_schema_version   VARCHAR(20) NOT NULL DEFAULT 'V14',
    last_migration_at       TIMESTAMP WITH TIME ZONE,
    migration_status        VARCHAR(50) NOT NULL DEFAULT 'SYNCED',
    
    -- Backup Configuration
    backup_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    backup_schedule         VARCHAR(50) NOT NULL DEFAULT '0 3 * * *',  -- Cron: 3 AM daily
    retention_days          INTEGER NOT NULL DEFAULT 30,
    last_backup_at          TIMESTAMP WITH TIME ZONE,
    last_backup_size_mb     INTEGER,
    
    -- Quotas (enforced by application)
    quota_storage_gb        INTEGER NOT NULL DEFAULT 10,
    quota_orders_per_day    INTEGER NOT NULL DEFAULT 10000,
    quota_connections_max   INTEGER NOT NULL DEFAULT 20,
    
    -- Status
    status                  VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT tenant_databases_code_unique UNIQUE (tenant_code),
    CONSTRAINT tenant_databases_dbname_unique UNIQUE (database_name),
    CONSTRAINT tenant_databases_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'MIGRATING', 'ARCHIVED')),
    CONSTRAINT tenant_databases_migration_status_check CHECK (migration_status IN ('SYNCED', 'PENDING', 'FAILED', 'RUNNING'))
);

-- Indexes for DatasourceRouter lookups
CREATE INDEX idx_tenant_databases_code ON tenant_databases(tenant_code);
CREATE INDEX idx_tenant_databases_status ON tenant_databases(status);
CREATE INDEX idx_tenant_databases_owner ON tenant_databases(owner_id) WHERE owner_id IS NOT NULL;

COMMENT ON TABLE tenant_databases IS 'Central catalog of all tenant databases in the multi-tenant platform';
COMMENT ON COLUMN tenant_databases.tenant_code IS 'Unique identifier used in API headers (X-Tenant-ID) and routing';
COMMENT ON COLUMN tenant_databases.current_schema_version IS 'Last successfully applied migration (e.g., V11)';
COMMENT ON COLUMN tenant_databases.target_schema_version IS 'Expected schema version - app fails startup if current != target';
COMMENT ON COLUMN tenant_databases.backup_schedule IS 'Cron expression for automated backups';

-- =============================================================================
-- TENANT API KEYS
-- =============================================================================
-- API keys for programmatic tenant identification.
-- Each tenant can have multiple keys (production, staging, etc.)
-- =============================================================================
CREATE TABLE tenant_api_keys (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID NOT NULL REFERENCES tenant_databases(id) ON DELETE CASCADE,
    
    -- Key Identity
    key_name                VARCHAR(100) NOT NULL,      -- e.g., 'production', 'staging'
    api_key                 VARCHAR(64) NOT NULL,       -- The actual key (hashed in prod)
    key_prefix              VARCHAR(10) NOT NULL,       -- e.g., 'gf17_' for display
    
    -- Permissions
    permissions             JSONB NOT NULL DEFAULT '["read", "write"]',
    rate_limit_per_minute   INTEGER NOT NULL DEFAULT 1000,
    
    -- Status
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at            TIMESTAMP WITH TIME ZONE,
    expires_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT tenant_api_keys_key_unique UNIQUE (api_key),
    CONSTRAINT tenant_api_keys_name_per_tenant UNIQUE (tenant_id, key_name)
);

CREATE INDEX idx_tenant_api_keys_key ON tenant_api_keys(api_key) WHERE is_active = TRUE;
CREATE INDEX idx_tenant_api_keys_tenant ON tenant_api_keys(tenant_id);

COMMENT ON TABLE tenant_api_keys IS 'API keys for authenticating and routing tenant requests';

-- =============================================================================
-- TENANT USAGE METRICS
-- =============================================================================
-- Daily snapshots of tenant resource usage for quota enforcement and billing.
-- =============================================================================
CREATE TABLE tenant_usage_metrics (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID NOT NULL REFERENCES tenant_databases(id) ON DELETE CASCADE,
    
    -- Time Period
    metric_date             DATE NOT NULL,
    
    -- Storage Metrics
    database_size_mb        INTEGER NOT NULL DEFAULT 0,
    table_count             INTEGER NOT NULL DEFAULT 0,
    row_count_estimate      BIGINT NOT NULL DEFAULT 0,
    
    -- Activity Metrics
    orders_created          INTEGER NOT NULL DEFAULT 0,
    orders_completed        INTEGER NOT NULL DEFAULT 0,
    api_calls               INTEGER NOT NULL DEFAULT 0,
    
    -- Connection Metrics
    peak_connections        INTEGER NOT NULL DEFAULT 0,
    avg_query_time_ms       INTEGER NOT NULL DEFAULT 0,
    
    -- Quota Status
    storage_quota_used_pct  DECIMAL(5,2) NOT NULL DEFAULT 0,
    orders_quota_used_pct   DECIMAL(5,2) NOT NULL DEFAULT 0,
    
    -- Timestamps
    collected_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT tenant_usage_metrics_unique UNIQUE (tenant_id, metric_date)
);

CREATE INDEX idx_tenant_usage_metrics_tenant_date ON tenant_usage_metrics(tenant_id, metric_date DESC);

COMMENT ON TABLE tenant_usage_metrics IS 'Daily usage snapshots for quota enforcement and billing';

-- =============================================================================
-- SEED DATA: Register the main tenant
-- =============================================================================
INSERT INTO tenant_databases (
    tenant_code,
    database_name,
    display_name,
    owner_email,
    host,
    port,
    current_schema_version,
    target_schema_version,
    backup_schedule,
    retention_days,
    quota_storage_gb,
    quota_orders_per_day,
    status
) VALUES (
    'goodfellaz17',
    'spotifybot',
    'Goodfellaz17 Production',
    'admin@goodfellaz17.com',
    'localhost',
    5432,
    'V12',
    'V14',
    '0 3 * * *',
    30,
    50,
    100000,
    'ACTIVE'
);

-- Generate initial API key for goodfellaz17
INSERT INTO tenant_api_keys (
    tenant_id,
    key_name,
    api_key,
    key_prefix,
    permissions,
    rate_limit_per_minute
) VALUES (
    (SELECT id FROM tenant_databases WHERE tenant_code = 'goodfellaz17'),
    'production',
    'gf17_prod_' || encode(gen_random_bytes(24), 'hex'),
    'gf17_',
    '["read", "write", "admin"]',
    5000
);

-- =============================================================================
-- HELPER VIEWS
-- =============================================================================

-- View: Tenant status dashboard
CREATE OR REPLACE VIEW v_tenant_status AS
SELECT 
    td.tenant_code,
    td.display_name,
    td.database_name,
    td.status,
    td.current_schema_version,
    td.target_schema_version,
    CASE WHEN td.current_schema_version != td.target_schema_version 
         THEN 'NEEDS_MIGRATION' ELSE 'SYNCED' END AS schema_status,
    td.last_backup_at,
    td.last_backup_size_mb,
    COALESCE(tum.database_size_mb, 0) AS current_size_mb,
    td.quota_storage_gb * 1024 AS quota_storage_mb,
    ROUND(COALESCE(tum.database_size_mb, 0)::numeric / (td.quota_storage_gb * 1024) * 100, 2) AS storage_used_pct
FROM tenant_databases td
LEFT JOIN LATERAL (
    SELECT database_size_mb 
    FROM tenant_usage_metrics 
    WHERE tenant_id = td.id 
    ORDER BY metric_date DESC 
    LIMIT 1
) tum ON true;

COMMENT ON VIEW v_tenant_status IS 'Dashboard view of all tenant databases with schema and quota status';

-- =============================================================================
-- FUNCTIONS
-- =============================================================================

-- Function: Get tenant by API key (used by DatasourceRouter)
CREATE OR REPLACE FUNCTION get_tenant_by_api_key(p_api_key VARCHAR)
RETURNS TABLE (
    tenant_code VARCHAR,
    database_name VARCHAR,
    host VARCHAR,
    port INTEGER,
    connection_pool_size INTEGER,
    permissions JSONB
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        td.tenant_code,
        td.database_name,
        td.host,
        td.port,
        td.connection_pool_size,
        tak.permissions
    FROM tenant_api_keys tak
    JOIN tenant_databases td ON tak.tenant_id = td.id
    WHERE tak.api_key = p_api_key
      AND tak.is_active = TRUE
      AND td.status = 'ACTIVE'
      AND (tak.expires_at IS NULL OR tak.expires_at > CURRENT_TIMESTAMP);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_tenant_by_api_key IS 'Resolves API key to tenant connection details for routing';

-- Function: Update tenant schema version after migration
CREATE OR REPLACE FUNCTION update_tenant_schema_version(
    p_tenant_code VARCHAR,
    p_new_version VARCHAR
) RETURNS VOID AS $$
BEGIN
    UPDATE tenant_databases
    SET current_schema_version = p_new_version,
        last_migration_at = CURRENT_TIMESTAMP,
        migration_status = 'SYNCED',
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_code = p_tenant_code;
END;
$$ LANGUAGE plpgsql;

-- Function: Check if tenant can create more orders today (quota enforcement)
CREATE OR REPLACE FUNCTION check_tenant_order_quota(p_tenant_code VARCHAR)
RETURNS BOOLEAN AS $$
DECLARE
    v_quota INTEGER;
    v_used INTEGER;
BEGIN
    -- Get tenant quota
    SELECT quota_orders_per_day INTO v_quota
    FROM tenant_databases
    WHERE tenant_code = p_tenant_code AND status = 'ACTIVE';
    
    IF v_quota IS NULL THEN
        RETURN FALSE;  -- Tenant not found or inactive
    END IF;
    
    -- Count today's orders (from tenant_usage_metrics or live count)
    SELECT COALESCE(orders_created, 0) INTO v_used
    FROM tenant_usage_metrics
    WHERE tenant_id = (SELECT id FROM tenant_databases WHERE tenant_code = p_tenant_code)
      AND metric_date = CURRENT_DATE;
    
    RETURN COALESCE(v_used, 0) < v_quota;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_tenant_order_quota IS 'Returns TRUE if tenant has not exceeded daily order quota';
