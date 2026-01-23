-- =============================================================================
-- Flyway Migration: V14__Retention_Policies.sql
-- 
-- Purpose: Automated data lifecycle management to keep database lean
-- 
-- Retention Rules:
--   - refund_events: 90 days (delete)
--   - balance_transactions: 1 year (summarize to monthly, delete raw)
--   - proxy_metrics: 30 days (aggregate to hourly, delete raw)
--   - audit_events: 2 years (archive to cold storage, then delete)
--
-- Benefits:
--   - Faster backups (smaller database)
--   - Better query performance (less data to scan)
--   - Compliance ready (configurable retention per data type)
--
-- @author RWTH Research Project
-- @version 1.0.0
-- =============================================================================

-- =============================================================================
-- RETENTION POLICIES TABLE
-- =============================================================================
-- Configuration table for data retention rules.
-- =============================================================================
CREATE TABLE retention_policies (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Policy Identity
    policy_name             VARCHAR(100) NOT NULL,
    table_name              VARCHAR(100) NOT NULL,
    description             VARCHAR(500),
    
    -- Retention Configuration
    retention_days          INTEGER NOT NULL,           -- Days to keep raw data
    archive_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    archive_table_name      VARCHAR(100),               -- Where to archive before delete
    aggregation_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    aggregation_interval    VARCHAR(20),                -- 'HOURLY', 'DAILY', 'MONTHLY'
    
    -- Execution Schedule
    cron_schedule           VARCHAR(50) NOT NULL DEFAULT '0 4 * * *',  -- 4 AM daily
    last_executed_at        TIMESTAMP WITH TIME ZONE,
    last_execution_status   VARCHAR(50),
    last_rows_deleted       INTEGER DEFAULT 0,
    last_rows_archived      INTEGER DEFAULT 0,
    last_execution_duration_ms INTEGER DEFAULT 0,
    
    -- Status
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT retention_policies_name_unique UNIQUE (policy_name),
    CONSTRAINT retention_policies_table_unique UNIQUE (table_name)
);

COMMENT ON TABLE retention_policies IS 'Configurable data retention rules for each table';

-- =============================================================================
-- RETENTION EXECUTION LOG
-- =============================================================================
-- History of retention job executions for auditing and debugging.
-- =============================================================================
CREATE TABLE retention_execution_log (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_id               UUID NOT NULL REFERENCES retention_policies(id),
    
    -- Execution Details
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            TIMESTAMP WITH TIME ZONE,
    status                  VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    
    -- Results
    rows_scanned            INTEGER DEFAULT 0,
    rows_deleted            INTEGER DEFAULT 0,
    rows_archived           INTEGER DEFAULT 0,
    rows_aggregated         INTEGER DEFAULT 0,
    disk_space_freed_mb     INTEGER DEFAULT 0,
    
    -- Error Handling
    error_message           TEXT,
    
    CONSTRAINT retention_execution_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_retention_log_policy ON retention_execution_log(policy_id, started_at DESC);

-- =============================================================================
-- AGGREGATED TABLES FOR HISTORICAL DATA
-- =============================================================================

-- Hourly proxy metrics (aggregated from raw proxy_metrics)
CREATE TABLE proxy_metrics_hourly (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    proxy_id                UUID NOT NULL,
    
    -- Time bucket
    hour_timestamp          TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Aggregated metrics
    total_requests          INTEGER NOT NULL DEFAULT 0,
    successful_requests     INTEGER NOT NULL DEFAULT 0,
    failed_requests         INTEGER NOT NULL DEFAULT 0,
    avg_latency_ms          INTEGER NOT NULL DEFAULT 0,
    min_latency_ms          INTEGER NOT NULL DEFAULT 0,
    max_latency_ms          INTEGER NOT NULL DEFAULT 0,
    p95_latency_ms          INTEGER NOT NULL DEFAULT 0,
    
    -- Calculated
    success_rate            DECIMAL(5,4) NOT NULL DEFAULT 0,
    
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT proxy_metrics_hourly_unique UNIQUE (proxy_id, hour_timestamp)
);

CREATE INDEX idx_proxy_metrics_hourly_proxy ON proxy_metrics_hourly(proxy_id, hour_timestamp DESC);

-- Monthly balance transaction summaries
CREATE TABLE balance_transactions_monthly (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID NOT NULL,
    
    -- Time bucket
    month_year              DATE NOT NULL,  -- First day of month
    
    -- Aggregated by transaction type
    total_deposits          DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_charges           DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_refunds           DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_bonuses           DECIMAL(12,2) NOT NULL DEFAULT 0,
    
    -- Counts
    deposit_count           INTEGER NOT NULL DEFAULT 0,
    charge_count            INTEGER NOT NULL DEFAULT 0,
    refund_count            INTEGER NOT NULL DEFAULT 0,
    
    -- Net change
    net_change              DECIMAL(12,2) NOT NULL DEFAULT 0,
    ending_balance          DECIMAL(12,2),
    
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT balance_transactions_monthly_unique UNIQUE (user_id, month_year)
);

CREATE INDEX idx_balance_monthly_user ON balance_transactions_monthly(user_id, month_year DESC);

-- =============================================================================
-- SEED: Default Retention Policies
-- =============================================================================

-- Policy 1: Refund events - delete after 90 days
INSERT INTO retention_policies (
    policy_name, table_name, description,
    retention_days, archive_enabled, aggregation_enabled,
    cron_schedule, is_active
) VALUES (
    'refund_events_cleanup',
    'refund_events',
    'Delete refund event records older than 90 days',
    90, FALSE, FALSE,
    '0 4 1 * *',  -- Monthly at 4 AM on 1st
    TRUE
);

-- Policy 2: Balance transactions - aggregate to monthly after 1 year
INSERT INTO retention_policies (
    policy_name, table_name, description,
    retention_days, archive_enabled, aggregation_enabled, aggregation_interval,
    cron_schedule, is_active
) VALUES (
    'balance_transactions_aggregate',
    'balance_transactions',
    'Aggregate to monthly summaries after 1 year, delete raw records',
    365, FALSE, TRUE, 'MONTHLY',
    '0 3 1 * *',  -- Monthly at 3 AM on 1st
    TRUE
);

-- Policy 3: Proxy metrics - aggregate to hourly after 30 days
INSERT INTO retention_policies (
    policy_name, table_name, description,
    retention_days, archive_enabled, aggregation_enabled, aggregation_interval,
    cron_schedule, is_active
) VALUES (
    'proxy_metrics_aggregate',
    'proxy_metrics',
    'Aggregate to hourly summaries after 30 days, delete raw records',
    30, FALSE, TRUE, 'HOURLY',
    '0 5 * * *',  -- Daily at 5 AM
    TRUE
);

-- Policy 4: Audit events - archive after 2 years
INSERT INTO retention_policies (
    policy_name, table_name, description,
    retention_days, archive_enabled, archive_table_name, aggregation_enabled,
    cron_schedule, is_active
) VALUES (
    'audit_events_archive',
    'audit_events',
    'Archive audit events older than 2 years to cold storage',
    730, TRUE, 'audit_events_archive', FALSE,
    '0 2 1 * *',  -- Monthly at 2 AM on 1st
    TRUE
);

-- =============================================================================
-- RETENTION FUNCTIONS
-- =============================================================================

-- Function: Execute a single retention policy
CREATE OR REPLACE FUNCTION execute_retention_policy(p_policy_name VARCHAR)
RETURNS TABLE (
    rows_deleted INTEGER,
    rows_archived INTEGER,
    rows_aggregated INTEGER,
    duration_ms INTEGER
) AS $$
DECLARE
    v_policy RECORD;
    v_start_time TIMESTAMP;
    v_cutoff_date TIMESTAMP;
    v_deleted INTEGER := 0;
    v_archived INTEGER := 0;
    v_aggregated INTEGER := 0;
    v_log_id UUID;
BEGIN
    v_start_time := clock_timestamp();
    
    -- Get policy configuration
    SELECT * INTO v_policy
    FROM retention_policies
    WHERE policy_name = p_policy_name AND is_active = TRUE;
    
    IF v_policy IS NULL THEN
        RAISE EXCEPTION 'Policy not found or inactive: %', p_policy_name;
    END IF;
    
    v_cutoff_date := CURRENT_TIMESTAMP - (v_policy.retention_days || ' days')::INTERVAL;
    
    -- Create execution log entry
    INSERT INTO retention_execution_log (policy_id, status)
    VALUES (v_policy.id, 'RUNNING')
    RETURNING id INTO v_log_id;
    
    BEGIN
        -- Handle proxy_metrics aggregation
        IF v_policy.table_name = 'proxy_metrics' AND v_policy.aggregation_enabled THEN
            -- Aggregate to hourly
            INSERT INTO proxy_metrics_hourly (
                proxy_id, hour_timestamp,
                total_requests, successful_requests, failed_requests,
                avg_latency_ms, min_latency_ms, max_latency_ms, success_rate
            )
            SELECT 
                proxy_id,
                date_trunc('hour', recorded_at) AS hour_timestamp,
                COUNT(*) AS total_requests,
                COUNT(*) FILTER (WHERE success = TRUE) AS successful_requests,
                COUNT(*) FILTER (WHERE success = FALSE) AS failed_requests,
                AVG(latency_ms)::INTEGER AS avg_latency_ms,
                MIN(latency_ms) AS min_latency_ms,
                MAX(latency_ms) AS max_latency_ms,
                COUNT(*) FILTER (WHERE success = TRUE)::DECIMAL / NULLIF(COUNT(*), 0) AS success_rate
            FROM proxy_metrics
            WHERE recorded_at < v_cutoff_date
            GROUP BY proxy_id, date_trunc('hour', recorded_at)
            ON CONFLICT (proxy_id, hour_timestamp) DO NOTHING;
            
            GET DIAGNOSTICS v_aggregated = ROW_COUNT;
            
            -- Delete raw records
            DELETE FROM proxy_metrics WHERE recorded_at < v_cutoff_date;
            GET DIAGNOSTICS v_deleted = ROW_COUNT;
        
        -- Handle balance_transactions aggregation
        ELSIF v_policy.table_name = 'balance_transactions' AND v_policy.aggregation_enabled THEN
            INSERT INTO balance_transactions_monthly (
                user_id, month_year,
                total_deposits, total_charges, total_refunds, total_bonuses,
                deposit_count, charge_count, refund_count, net_change
            )
            SELECT 
                user_id,
                date_trunc('month', created_at)::DATE AS month_year,
                COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'DEPOSIT'), 0) AS total_deposits,
                COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'ORDER_CHARGE'), 0) AS total_charges,
                COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'REFUND'), 0) AS total_refunds,
                COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'BONUS'), 0) AS total_bonuses,
                COUNT(*) FILTER (WHERE transaction_type = 'DEPOSIT') AS deposit_count,
                COUNT(*) FILTER (WHERE transaction_type = 'ORDER_CHARGE') AS charge_count,
                COUNT(*) FILTER (WHERE transaction_type = 'REFUND') AS refund_count,
                COALESCE(SUM(amount) FILTER (WHERE transaction_type IN ('DEPOSIT', 'REFUND', 'BONUS')), 0) -
                COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'ORDER_CHARGE'), 0) AS net_change
            FROM balance_transactions
            WHERE created_at < v_cutoff_date
            GROUP BY user_id, date_trunc('month', created_at)
            ON CONFLICT (user_id, month_year) DO NOTHING;
            
            GET DIAGNOSTICS v_aggregated = ROW_COUNT;
            
            DELETE FROM balance_transactions WHERE created_at < v_cutoff_date;
            GET DIAGNOSTICS v_deleted = ROW_COUNT;
        
        -- Handle simple deletion (refund_events, etc.)
        ELSE
            EXECUTE format(
                'DELETE FROM %I WHERE created_at < $1',
                v_policy.table_name
            ) USING v_cutoff_date;
            GET DIAGNOSTICS v_deleted = ROW_COUNT;
        END IF;
        
        -- Update execution log
        UPDATE retention_execution_log
        SET completed_at = clock_timestamp(),
            status = 'COMPLETED',
            rows_deleted = v_deleted,
            rows_archived = v_archived,
            rows_aggregated = v_aggregated
        WHERE id = v_log_id;
        
        -- Update policy stats
        UPDATE retention_policies
        SET last_executed_at = clock_timestamp(),
            last_execution_status = 'COMPLETED',
            last_rows_deleted = v_deleted,
            last_rows_archived = v_archived,
            last_execution_duration_ms = EXTRACT(MILLISECONDS FROM (clock_timestamp() - v_start_time))::INTEGER,
            updated_at = clock_timestamp()
        WHERE id = v_policy.id;
        
    EXCEPTION WHEN OTHERS THEN
        UPDATE retention_execution_log
        SET completed_at = clock_timestamp(),
            status = 'FAILED',
            error_message = SQLERRM
        WHERE id = v_log_id;
        
        UPDATE retention_policies
        SET last_execution_status = 'FAILED',
            updated_at = clock_timestamp()
        WHERE id = v_policy.id;
        
        RAISE;
    END;
    
    RETURN QUERY SELECT 
        v_deleted, 
        v_archived, 
        v_aggregated,
        EXTRACT(MILLISECONDS FROM (clock_timestamp() - v_start_time))::INTEGER;
END;
$$ LANGUAGE plpgsql;

-- Function: Execute all active retention policies
CREATE OR REPLACE FUNCTION execute_all_retention_policies()
RETURNS TABLE (
    policy_name VARCHAR,
    rows_deleted INTEGER,
    rows_archived INTEGER,
    rows_aggregated INTEGER,
    status VARCHAR
) AS $$
DECLARE
    v_policy RECORD;
    v_result RECORD;
BEGIN
    FOR v_policy IN 
        SELECT rp.policy_name 
        FROM retention_policies rp 
        WHERE rp.is_active = TRUE
    LOOP
        BEGIN
            SELECT * INTO v_result FROM execute_retention_policy(v_policy.policy_name);
            RETURN QUERY SELECT 
                v_policy.policy_name,
                v_result.rows_deleted,
                v_result.rows_archived,
                v_result.rows_aggregated,
                'COMPLETED'::VARCHAR;
        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT 
                v_policy.policy_name,
                0::INTEGER,
                0::INTEGER,
                0::INTEGER,
                ('FAILED: ' || SQLERRM)::VARCHAR;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION execute_all_retention_policies IS 'Run all active retention policies - call from cron job';

-- =============================================================================
-- VIEW: Retention Policy Dashboard
-- =============================================================================
CREATE OR REPLACE VIEW v_retention_dashboard AS
SELECT 
    rp.policy_name,
    rp.table_name,
    rp.retention_days,
    rp.aggregation_enabled,
    rp.is_active,
    rp.last_executed_at,
    rp.last_execution_status,
    rp.last_rows_deleted,
    rp.last_execution_duration_ms,
    CASE 
        WHEN rp.last_executed_at IS NULL THEN 'NEVER_RUN'
        WHEN rp.last_executed_at < CURRENT_TIMESTAMP - INTERVAL '2 days' THEN 'OVERDUE'
        ELSE 'ON_SCHEDULE'
    END AS schedule_status
FROM retention_policies rp
ORDER BY rp.policy_name;

COMMENT ON VIEW v_retention_dashboard IS 'Dashboard view of all retention policies and their status';
