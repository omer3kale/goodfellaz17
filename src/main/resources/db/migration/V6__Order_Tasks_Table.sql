-- ============================================================================
-- V6__Order_Tasks_Table.sql
-- 
-- Creates the order_tasks table for granular 15k order delivery.
-- Each task represents a small batch of plays (200-500) that is:
-- - Atomic (all or nothing)
-- - Idempotent (no double-counting)
-- - Retryable (with exponential backoff)
-- - Checkpointed (survives restarts)
-- ============================================================================

-- Create task status enum type for reference (stored as VARCHAR for flexibility)
-- Status values: PENDING, EXECUTING, COMPLETED, FAILED_RETRYING, FAILED_PERMANENT

CREATE TABLE IF NOT EXISTS order_tasks (
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
    
    -- Unique constraint on order + sequence (no duplicate task numbers)
    CONSTRAINT uq_order_task_sequence UNIQUE (order_id, sequence_number),
    
    -- Unique constraint on idempotency token (prevent duplicate execution)
    CONSTRAINT uq_task_idempotency_token UNIQUE (idempotency_token)
);

-- ============================================================================
-- INDEXES for performance
-- ============================================================================

-- Index for worker pickup query (pending tasks by scheduled time)
CREATE INDEX IF NOT EXISTS idx_order_tasks_pending_scheduled 
    ON order_tasks (scheduled_at ASC) 
    WHERE status = 'PENDING';

-- Index for retry query (failed tasks past retry time)
CREATE INDEX IF NOT EXISTS idx_order_tasks_retrying 
    ON order_tasks (retry_after ASC) 
    WHERE status = 'FAILED_RETRYING';

-- Index for orphan detection (executing tasks by start time)
CREATE INDEX IF NOT EXISTS idx_order_tasks_executing_started 
    ON order_tasks (execution_started_at ASC) 
    WHERE status = 'EXECUTING';

-- Index for order progress queries
CREATE INDEX IF NOT EXISTS idx_order_tasks_order_status 
    ON order_tasks (order_id, status);

-- Index for dead-letter queue queries
CREATE INDEX IF NOT EXISTS idx_order_tasks_failed_permanent 
    ON order_tasks (created_at DESC) 
    WHERE status = 'FAILED_PERMANENT';

-- Combined index for the main worker pickup query
CREATE INDEX IF NOT EXISTS idx_order_tasks_worker_pickup 
    ON order_tasks (status, scheduled_at, retry_after, execution_started_at);

-- ============================================================================
-- COMMENTS for documentation
-- ============================================================================

COMMENT ON TABLE order_tasks IS 'Granular execution units for 15k order delivery. Each task delivers 200-500 plays atomically.';
COMMENT ON COLUMN order_tasks.sequence_number IS 'Task sequence within order (1, 2, 3...) for ordering and idempotency';
COMMENT ON COLUMN order_tasks.status IS 'PENDING|EXECUTING|COMPLETED|FAILED_RETRYING|FAILED_PERMANENT';
COMMENT ON COLUMN order_tasks.attempts IS 'Number of execution attempts (incremented each try)';
COMMENT ON COLUMN order_tasks.max_attempts IS 'Maximum attempts before FAILED_PERMANENT (default 3)';
COMMENT ON COLUMN order_tasks.scheduled_at IS 'When task should execute (spread across 48-72h window)';
COMMENT ON COLUMN order_tasks.retry_after IS 'Earliest time to retry after transient failure (exponential backoff)';
COMMENT ON COLUMN order_tasks.idempotency_token IS 'Format: {orderId}:{sequenceNumber}:{attempt} - prevents double delivery';
COMMENT ON COLUMN order_tasks.worker_id IS 'ID of worker processing this task (for orphan detection)';

-- ============================================================================
-- Add columns to orders table for dead-letter tracking
-- ============================================================================

-- Track failed-permanent plays in the order itself
ALTER TABLE orders 
    ADD COLUMN IF NOT EXISTS failed_permanent_plays INTEGER NOT NULL DEFAULT 0;

-- Track task-based delivery flag
ALTER TABLE orders 
    ADD COLUMN IF NOT EXISTS uses_task_delivery BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN orders.failed_permanent_plays IS 'Count of plays that permanently failed (dead-letter queue)';
COMMENT ON COLUMN orders.uses_task_delivery IS 'True if order uses task-based delivery (15k+ orders)';
