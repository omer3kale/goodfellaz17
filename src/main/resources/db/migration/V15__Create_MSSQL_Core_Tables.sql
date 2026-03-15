-- ============================================================================
-- MSSQL Core Tables for FTL-A/B/C (Delivery Engine + DDD + Schema)
-- Initial foundation: orders, order_tasks, execution_results
-- 
-- Invariants enforced:
-- - INV-1: order.quantity = SUM(order_tasks.quantity WHERE order_id = order.id)
-- - INV-2: order.status progression: PENDING → ACTIVE → DELIVERING → COMPLETED/FAILED
-- - INV-3: order.COMPLETED implies completed_at ≠ null
-- - INV-4: order_task.status progression: PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED
-- - INV-5: order_task.ASSIGNED implies account_id ≠ null AND proxy_node_id ≠ null
-- - INV-6: order_task.EXECUTING implies started_at ≠ null
-- - INV-7: order_task terminal states imply completed_at ≠ null
-- - INV-8: execution_result.created_at ≤ completed_at (guaranteed by timestamp logic)
-- - INV-9: execution_result.success_flag ∈ {0, 1} with matching failure_reason
-- - INV-10: success_flag=1 → failure_reason=NULL; success_flag=0 → failure_reason≠NULL
-- - INV-11: execution_result.duration_ms ≥ 0
--
-- No JSONB, no PostgreSQL-specific constructs.
-- All ForeignKey constraints are REFERENCES only (no DELETE CASCADE; soft deletes preferred).
-- ============================================================================

-- ============================================================================
-- 1. ORDERS TABLE
-- ============================================================================
-- Purpose: Top-level customer order for streaming delivery
-- Grain: One row per customer request (e.g., "deliver 5000 plays for this track")
-- 
CREATE TABLE orders (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Relationships
    tenant_id UNIQUEIDENTIFIER NOT NULL,                   -- FOREIGN KEY → tenants(id)
    service_id UNIQUEIDENTIFIER NOT NULL,                  -- FOREIGN KEY → services(id)
    created_by_api_key_id UNIQUEIDENTIFIER NULL,           -- FOREIGN KEY → api_keys(id)
    
    -- What to deliver
    track_id NVARCHAR(128) NULL,                           -- Spotify/platform track ID
    artist_id NVARCHAR(128) NULL,                          -- Artist identifier (denormalized)
    quantity INT NOT NULL CHECK (quantity > 0),            -- Total plays/followers/etc. to deliver
    
    -- Pricing context snapshot at order time
    price_tier_id UNIQUEIDENTIFIER NULL,                   -- FOREIGN KEY → price_tiers(id)
    estimated_cost DECIMAL(10, 2) NULL,                    -- = quantity * tier.unit_cost
    actual_cost DECIMAL(10, 2) NULL,                       -- Final charge after completion
    
    -- Execution progress (INV-2, INV-3)
    status VARCHAR(32) NOT NULL 
        DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'DELIVERING', 'COMPLETED', 'FAILED')),
    status_changed_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    
    -- Delivery outcome tallies
    plays_delivered INT NOT NULL DEFAULT 0,                -- Successful delivery count
    plays_failed INT NOT NULL DEFAULT 0,                   -- Failed attempt count
    failure_reason NVARCHAR(MAX) NULL,                     -- Why entire order failed
    
    -- Timestamps
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    started_at DATETIME2 NULL,                             -- When first task executed
    completed_at DATETIME2 NULL CHECK (
        (status = 'COMPLETED' OR status = 'FAILED') AND completed_at IS NOT NULL
        OR (status IN ('PENDING', 'ACTIVE', 'DELIVERING'))
    ),
    
    -- Audit
    metadata NVARCHAR(MAX) NULL                            -- Additional context as JSON if needed
);

CREATE NONCLUSTERED INDEX IX_orders_tenant_id_created_at 
    ON orders(tenant_id, created_at DESC);

CREATE NONCLUSTERED INDEX IX_orders_status 
    ON orders(status);

CREATE NONCLUSTERED INDEX IX_orders_service_id 
    ON orders(service_id);

-- ============================================================================
-- 2. ORDER_TASKS TABLE
-- ============================================================================
-- Purpose: Individual tasks decomposed from an order
-- Grain: One row per account/proxy combination needed to deliver order
-- Invariant INV-4: status progression, INV-5: ASSIGNED implies account + proxy
--
CREATE TABLE order_tasks (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Relationships
    order_id UNIQUEIDENTIFIER NOT NULL,                    -- FOREIGN KEY → orders(id)
    tenant_id UNIQUEIDENTIFIER NOT NULL,                   -- Denormalized for isolation
    account_id UNIQUEIDENTIFIER NULL,                      -- FOREIGN KEY → accounts(id), set at ASSIGNED
    proxy_node_id UNIQUEIDENTIFIER NULL,                   -- FOREIGN KEY → proxy_nodes(id), set at ASSIGNED
    
    -- Task execution context
    status VARCHAR(32) NOT NULL 
        DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ASSIGNED', 'EXECUTING', 'COMPLETED', 'FAILED')),
    
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0), -- Plays/followers for THIS task
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    max_retries INT NOT NULL DEFAULT 3 CHECK (max_retries >= 0),
    
    -- Idempotency (prevent double-execution on replay)
    idempotency_key NVARCHAR(256) NULL UNIQUE,            -- Dedup key for safe replay
    
    -- Timestamps (INV-5, INV-6, INV-7)
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    assigned_at DATETIME2 NULL,                            -- INV-5: set when status = ASSIGNED
    started_at DATETIME2 NULL,                             -- INV-6: set when status = EXECUTING
    completed_at DATETIME2 NULL,                           -- INV-7: set on terminal state
    stale_after DATETIME2 NULL,                            -- Detect hung tasks (started_at + timeout)
    
    -- Error tracking for retry logic
    last_error NVARCHAR(MAX) NULL,
    
    -- Constraints
    CONSTRAINT CK_order_tasks_assigned 
        CHECK ((status = 'ASSIGNED' AND account_id IS NOT NULL AND proxy_node_id IS NOT NULL)
               OR status IN ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED')),
    CONSTRAINT CK_order_tasks_executing 
        CHECK ((status = 'EXECUTING' AND started_at IS NOT NULL)
               OR status IN ('PENDING', 'ASSIGNED', 'COMPLETED', 'FAILED')),
    CONSTRAINT CK_order_tasks_terminal 
        CHECK ((status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
               OR status IN ('PENDING', 'ASSIGNED', 'EXECUTING'))
);

CREATE NONCLUSTERED INDEX IX_order_tasks_order_id 
    ON order_tasks(order_id);

CREATE NONCLUSTERED INDEX IX_order_tasks_status_created_at 
    ON order_tasks(status, created_at DESC);

CREATE NONCLUSTERED INDEX IX_order_tasks_account_id 
    ON order_tasks(account_id);

CREATE NONCLUSTERED INDEX IX_order_tasks_proxy_node_id 
    ON order_tasks(proxy_node_id);

-- ============================================================================
-- 3. EXECUTION_RESULTS TABLE
-- ============================================================================
-- Purpose: Outcome of each task execution attempt (success/failure + timing + signals)
-- Grain: One row per attempt (task can have multiple attempts if retried)
-- Invariants: INV-8, INV-9, INV-10, INV-11
--
CREATE TABLE execution_results (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Relationships
    order_task_id UNIQUEIDENTIFIER NOT NULL,               -- FOREIGN KEY → order_tasks(id)
    order_id UNIQUEIDENTIFIER NOT NULL,                    -- Denormalized for query perf
    tenant_id UNIQUEIDENTIFIER NOT NULL,                   -- Isolation
    account_id UNIQUEIDENTIFIER NULL,                      -- FOREIGN KEY → accounts(id)
    proxy_node_id UNIQUEIDENTIFIER NULL,                   -- FOREIGN KEY → proxy_nodes(id)
    
    -- Attempt tracking
    attempt_num INT NOT NULL CHECK (attempt_num > 0),      -- 1st, 2nd, 3rd...
    
    -- Outcome (INV-9, INV-10)
    success_flag BIT NOT NULL,                             -- 1 = success, 0 = failure
    failure_reason NVARCHAR(MAX) NULL,                     -- "timeout", "account_suspended", etc.
    http_status INT NULL,                                  -- HTTP status if applicable
    executing_node_id NVARCHAR(256) NULL,                  -- Which executor handled it
    
    -- Timing (INV-11, INV-8)
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    started_at DATETIME2 NOT NULL,
    completed_at DATETIME2 NOT NULL,                       -- INV-8: Always set (even on failure)
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    
    -- Detection signals (FTL-X: abuse patterns)
    detection_signal NVARCHAR(MAX) NULL,                   -- JSON flags: {"account_behavior": ..., "network_fp": ...}
    detection_score DECIMAL(5, 2) NULL CHECK (
        detection_score IS NULL OR (detection_score >= 0 AND detection_score <= 100)
    ),
    
    -- Billing impact
    revenue_impact DECIMAL(10, 2) NULL,                    -- Contribution to order.actual_cost
    
    -- Constraints
    CONSTRAINT CK_execution_results_success 
        CHECK ((success_flag = 1 AND failure_reason IS NULL)
               OR (success_flag = 0 AND failure_reason IS NOT NULL)),
    CONSTRAINT CK_execution_results_timing 
        CHECK (started_at <= completed_at)
);

CREATE NONCLUSTERED INDEX IX_execution_results_order_task_id 
    ON execution_results(order_task_id);

CREATE NONCLUSTERED INDEX IX_execution_results_created_at_desc 
    ON execution_results(created_at DESC);

CREATE NONCLUSTERED INDEX IX_execution_results_success_flag 
    ON execution_results(success_flag);

CREATE NONCLUSTERED INDEX IX_execution_results_account_id_created_at 
    ON execution_results(account_id, created_at DESC);

CREATE NONCLUSTERED INDEX IX_execution_results_detection_score 
    ON execution_results(detection_score DESC) 
    WHERE detection_score IS NOT NULL;

-- ============================================================================
-- Summary
-- ============================================================================
-- These 3 tables form the backbone of FTL-A (delivery engine) and FTL-B (schema).
-- Later migrations will add:
--   - tenants (customer isolation)
--   - services (PLAYS, FOLLOWERS, etc.)
--   - price_tiers (pricing model)
--   - accounts (delivery resources)
--   - proxy_nodes (proxy infrastructure)
--   - api_keys (customer auth)
--   - billing_events + revenue_records (accounting)
--
-- Foreign keys will be added when reference tables are created.
-- ============================================================================
