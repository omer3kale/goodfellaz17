-- ============================================================================
-- V9__Refund_Hardening.sql
--
-- Adds safety constraints, audit trail, and concurrency support for refunds.
-- Implements:
--   1. Quantity cap constraint (delivered + failed <= quantity)
--   2. Refund cap constraint (refund_amount <= cost)
--   3. No-completed-refund constraint (COMPLETED tasks can't be refunded)
--   4. Immutable pricing trigger (cost, price_per_unit can't change after creation)
--   5. Refund audit table (append-only event log)
--   6. Optimized partial index for refund queries
--   7. Anomaly tracking table for reconciliation
-- ============================================================================

-- =============================================================================
-- 1. SAFETY CONSTRAINTS
-- =============================================================================

-- Prevent delivered + failed_permanent from exceeding quantity
-- This enforces quantity conservation: all plays must be accounted for
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_quantity_cap'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT chk_quantity_cap
            CHECK (delivered + COALESCE(failed_permanent_plays, 0) <= quantity);
    END IF;
END $$;

-- Prevent refunding more than the order cost
-- This is a financial safety rail
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_refund_cap'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT chk_refund_cap
            CHECK (COALESCE(refund_amount, 0) <= COALESCE(cost, total_cost, 0));
    END IF;
END $$;

-- Prevent marking COMPLETED tasks as refunded (logic error)
-- Only FAILED_PERMANENT tasks should ever have refunded=true
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_no_completed_refund'
    ) THEN
        ALTER TABLE order_tasks
            ADD CONSTRAINT chk_no_completed_refund
            CHECK (NOT (status = 'COMPLETED' AND refunded = TRUE));
    END IF;
END $$;

-- =============================================================================
-- 2. IMMUTABLE PRICING TRIGGER
-- =============================================================================
-- Once an order is charged (cost > 0), price cannot be modified.
-- This prevents refund manipulation and audit discrepancies.

CREATE OR REPLACE FUNCTION prevent_price_change() 
RETURNS TRIGGER AS $$
BEGIN
    -- Block changes to price_per_unit once set
    IF OLD.price_per_unit IS NOT NULL 
       AND NEW.price_per_unit IS DISTINCT FROM OLD.price_per_unit THEN
        RAISE EXCEPTION 'price_per_unit is immutable after order creation';
    END IF;
    
    -- Block changes to cost once charged (> 0)
    IF OLD.cost IS NOT NULL 
       AND OLD.cost > 0 
       AND NEW.cost IS DISTINCT FROM OLD.cost THEN
        RAISE EXCEPTION 'cost is immutable after order creation';
    END IF;
    
    -- Block changes to total_cost once charged (> 0)
    IF OLD.total_cost IS NOT NULL 
       AND OLD.total_cost > 0 
       AND NEW.total_cost IS DISTINCT FROM OLD.total_cost THEN
        RAISE EXCEPTION 'total_cost is immutable after order creation';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_orders_immutable_price ON orders;

CREATE TRIGGER trg_orders_immutable_price
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION prevent_price_change();

COMMENT ON FUNCTION prevent_price_change() IS 
    'Prevents modification of pricing fields after order creation to ensure refund integrity';

-- =============================================================================
-- 3. REFUND EVENTS AUDIT TABLE (Append-Only)
-- =============================================================================
-- Every refund action creates an immutable record here.
-- Used for: finance reconciliation, fraud detection, dispute resolution.

CREATE TABLE IF NOT EXISTS refund_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL,
    task_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    quantity        INTEGER NOT NULL,
    amount          NUMERIC(12,4) NOT NULL,
    price_per_unit  NUMERIC(12,8) NOT NULL,
    worker_id       VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Reference but don't cascade delete - audit records are permanent
    CONSTRAINT fk_refund_events_order FOREIGN KEY (order_id) 
        REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_events_task FOREIGN KEY (task_id) 
        REFERENCES order_tasks(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_events_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE RESTRICT,
        
    -- Uniqueness: one refund event per task (idempotency enforcement)
    CONSTRAINT uq_refund_event_task UNIQUE (task_id)
);

CREATE INDEX IF NOT EXISTS idx_refund_events_order 
    ON refund_events (order_id);

CREATE INDEX IF NOT EXISTS idx_refund_events_user 
    ON refund_events (user_id);

CREATE INDEX IF NOT EXISTS idx_refund_events_created 
    ON refund_events (created_at DESC);

COMMENT ON TABLE refund_events IS 
    'Append-only audit log of all refund credits. One row per refunded task.';

-- =============================================================================
-- 4. REFUND ANOMALIES TABLE (For Reconciliation)
-- =============================================================================
-- Records discrepancies detected by reconciliation jobs.

CREATE TABLE IF NOT EXISTS refund_anomalies (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID NOT NULL,
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    anomaly_type            VARCHAR(50) NOT NULL,
    expected_refund_amount  NUMERIC(12,4),
    actual_refund_amount    NUMERIC(12,4),
    expected_failed_plays   INTEGER,
    actual_failed_plays     INTEGER,
    refunded_task_count     INTEGER,
    severity                VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    resolved_at             TIMESTAMPTZ,
    resolution_notes        TEXT,
    
    CONSTRAINT fk_refund_anomalies_order FOREIGN KEY (order_id) 
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_anomaly_type CHECK (anomaly_type IN (
        'REFUND_AMOUNT_MISMATCH', 
        'FAILED_PLAYS_MISMATCH', 
        'ORPHAN_REFUND_EVENT',
        'DOUBLE_REFUND_DETECTED',
        'BALANCE_DRIFT'
    )),
    CONSTRAINT chk_anomaly_severity CHECK (severity IN (
        'INFO', 'WARNING', 'CRITICAL'
    ))
);

CREATE INDEX IF NOT EXISTS idx_refund_anomalies_unresolved 
    ON refund_anomalies (detected_at DESC) 
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_refund_anomalies_severity 
    ON refund_anomalies (severity) 
    WHERE resolved_at IS NULL AND severity IN ('WARNING', 'CRITICAL');

COMMENT ON TABLE refund_anomalies IS 
    'Records detected discrepancies between order aggregates and task-level refund data.';

-- =============================================================================
-- 5. OPTIMIZED PARTIAL INDEX FOR REFUND QUERIES
-- =============================================================================
-- Supports efficient SELECT ... FOR UPDATE SKIP LOCKED queries.

CREATE INDEX IF NOT EXISTS idx_tasks_refund_pending 
    ON order_tasks (order_id, status, refunded)
    WHERE status = 'FAILED_PERMANENT' AND refunded = FALSE;

COMMENT ON INDEX idx_tasks_refund_pending IS 
    'Partial index for finding unrefunded FAILED_PERMANENT tasks. Used with FOR UPDATE SKIP LOCKED.';

-- =============================================================================
-- 6. RECONCILIATION HELPER VIEW
-- =============================================================================
-- Provides quick aggregate comparison for terminal orders.

CREATE OR REPLACE VIEW v_order_refund_reconciliation AS
SELECT 
    o.id AS order_id,
    o.status,
    o.quantity,
    o.delivered,
    o.failed_permanent_plays,
    o.refund_amount AS recorded_refund,
    COALESCE(o.price_per_unit, o.cost / NULLIF(o.quantity, 0)) AS price_per_unit,
    COALESCE(task_summary.refunded_task_count, 0) AS refunded_task_count,
    COALESCE(task_summary.refunded_quantity, 0) AS refunded_quantity,
    COALESCE(task_summary.computed_refund, 0) AS computed_refund,
    CASE 
        WHEN o.refund_amount != COALESCE(task_summary.computed_refund, 0) 
        THEN 'REFUND_MISMATCH'
        WHEN o.failed_permanent_plays != COALESCE(task_summary.refunded_quantity, 0) 
        THEN 'FAILED_PLAYS_MISMATCH'
        ELSE 'OK'
    END AS reconciliation_status
FROM orders o
LEFT JOIN LATERAL (
    SELECT 
        COUNT(*) AS refunded_task_count,
        COALESCE(SUM(t.quantity), 0) AS refunded_quantity,
        COALESCE(SUM(t.quantity), 0) * COALESCE(o.price_per_unit, o.cost / NULLIF(o.quantity, 0)) AS computed_refund
    FROM order_tasks t
    WHERE t.order_id = o.id 
      AND t.refunded = TRUE
) task_summary ON TRUE
WHERE o.status IN ('COMPLETED', 'PARTIAL', 'CANCELLED', 'FAILED');

COMMENT ON VIEW v_order_refund_reconciliation IS 
    'Reconciliation view comparing order-level refund data with task-level aggregates.';

-- ============================================================================
-- END V9
-- ============================================================================
