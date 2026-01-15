-- ============================================================================
-- V8__Task_Refund_Tracking.sql
--
-- Adds refund tracking to order_tasks for idempotent balance credits.
-- When a task reaches FAILED_PERMANENT, we credit the user's balance.
-- The refunded flag prevents double-credits if the failure path runs twice.
-- ============================================================================

-- Add refunded flag to track which tasks have had their cost refunded
ALTER TABLE order_tasks ADD COLUMN IF NOT EXISTS refunded BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for finding unrefunded failed tasks (dead-letter queue processing)
CREATE INDEX IF NOT EXISTS idx_order_tasks_unrefunded_failed 
    ON order_tasks (order_id) 
    WHERE status = 'FAILED_PERMANENT' AND refunded = FALSE;

-- ============================================================================
-- COMMENTS
-- ============================================================================
COMMENT ON COLUMN order_tasks.refunded IS 'TRUE if balance was credited back for this failed task. Prevents double-refunds.';
