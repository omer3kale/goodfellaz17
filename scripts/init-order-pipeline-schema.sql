-- ============================================================================
-- Order Pipeline Schema Initialization
-- Reactive R2DBC-compatible tables (no JPA relationships, only simple columns)
-- Table names use 'pipeline_' prefix to distinguish from the global order system
-- Date: 2026-01-26
-- ============================================================================

-- ============================================================================
-- Pipeline Orders Table
-- ============================================================================
-- Represents a user's request to deliver plays to a track.
-- Lifecycle: PENDING → ACTIVE → DELIVERING → COMPLETED (or FAILED)
CREATE TABLE IF NOT EXISTS pipeline_orders (
    id UUID PRIMARY KEY,
    track_id VARCHAR(255) NOT NULL,           -- Spotify track ID being promoted
    quantity INTEGER NOT NULL,                 -- How many plays to deliver
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, ACTIVE, DELIVERING, COMPLETED, FAILED
    plays_delivered INTEGER NOT NULL DEFAULT 0,     -- Count of successful deliveries
    plays_failed INTEGER NOT NULL DEFAULT 0,        -- Count of failed attempts
    failure_reason VARCHAR(500),               -- Why order failed (if applicable)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Pipeline Order Tasks Table
-- ============================================================================
-- Each task represents delivering 1 play to a specific account.
-- Lifecycle: PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED
CREATE TABLE IF NOT EXISTS pipeline_order_tasks (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES pipeline_orders(id) ON DELETE CASCADE,
    account_id VARCHAR(255) NOT NULL,          -- Spotify account to play from
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, ASSIGNED, EXECUTING, COMPLETED, FAILED
    assigned_proxy_node VARCHAR(255),          -- Which proxy node is handling this
    failure_reason VARCHAR(500),               -- Why it failed
    retry_count INTEGER NOT NULL DEFAULT 0,    -- Number of retry attempts
    max_retries INTEGER NOT NULL DEFAULT 3,    -- Max retry limit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,                      -- When task started executing
    completed_at TIMESTAMP                     -- When task completed (success or failure)
);

-- ============================================================================
-- Create Indexes
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_pipeline_orders_status ON pipeline_orders(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_orders_track_id ON pipeline_orders(track_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_orders_created_at ON pipeline_orders(created_at);

CREATE INDEX IF NOT EXISTS idx_pipeline_order_tasks_order_id ON pipeline_order_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_order_tasks_status ON pipeline_order_tasks(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_order_tasks_account_id ON pipeline_order_tasks(account_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_order_tasks_assigned_proxy ON pipeline_order_tasks(assigned_proxy_node);
CREATE INDEX IF NOT EXISTS idx_pipeline_order_tasks_created_at ON pipeline_order_tasks(created_at);

-- ============================================================================
-- Verification Query
-- ============================================================================
-- Run after applying schema to verify tables exist:
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('pipeline_orders', 'pipeline_order_tasks');
