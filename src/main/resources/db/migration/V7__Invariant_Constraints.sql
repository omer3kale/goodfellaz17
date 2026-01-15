-- V7: Hard Invariant Constraints
-- ================================
-- Enforces business invariants at the database level for bulletproof reliability.
-- These constraints complement the application-level OrderInvariantValidator.

-- =============================================================================
-- CONSTRAINT 1: Quantity must be positive
-- =============================================================================
ALTER TABLE orders 
ADD CONSTRAINT chk_order_quantity_positive 
CHECK (quantity > 0);

-- =============================================================================
-- CONSTRAINT 2: Delivered cannot exceed quantity
-- =============================================================================
ALTER TABLE orders 
ADD CONSTRAINT chk_delivered_not_exceed_quantity 
CHECK (delivered >= 0 AND delivered <= quantity);

-- =============================================================================
-- CONSTRAINT 3: Failed permanent plays cannot exceed quantity
-- =============================================================================
ALTER TABLE orders 
ADD CONSTRAINT chk_failed_permanent_not_exceed_quantity 
CHECK (failed_permanent_plays IS NULL OR (failed_permanent_plays >= 0 AND failed_permanent_plays <= quantity));

-- =============================================================================
-- CONSTRAINT 4: Task quantity must be positive and bounded
-- =============================================================================
ALTER TABLE order_tasks 
ADD CONSTRAINT chk_task_quantity_positive 
CHECK (quantity > 0 AND quantity <= 1000);

-- =============================================================================
-- CONSTRAINT 5: Task attempts cannot exceed max_attempts
-- =============================================================================
ALTER TABLE order_tasks 
ADD CONSTRAINT chk_attempts_not_exceed_max 
CHECK (attempts >= 0 AND attempts <= max_attempts);

-- =============================================================================
-- CONSTRAINT 6: Sequence numbers must be non-negative
-- =============================================================================
ALTER TABLE order_tasks 
ADD CONSTRAINT chk_sequence_non_negative 
CHECK (sequence_number >= 0);

-- =============================================================================
-- CONSTRAINT 7: Valid task status values
-- =============================================================================
ALTER TABLE order_tasks 
ADD CONSTRAINT chk_valid_task_status 
CHECK (status IN ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED_RETRYING', 'FAILED_PERMANENT'));

-- =============================================================================
-- CONSTRAINT 8: Unique idempotency token per order
-- =============================================================================
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_idempotency_token_per_order 
ON order_tasks(order_id, idempotency_token) 
WHERE idempotency_token IS NOT NULL;

-- =============================================================================
-- INVARIANT TRACKING TABLE
-- =============================================================================
-- Stores results of periodic invariant checks for audit/alerting.

CREATE TABLE IF NOT EXISTS invariant_check_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    check_type VARCHAR(50) NOT NULL,            -- 'ORDER', 'GLOBAL', 'ORPHAN'
    order_id UUID,                               -- NULL for global checks
    passed BOOLEAN NOT NULL,
    violations_count INTEGER DEFAULT 0,
    violation_details JSONB,                     -- Details of any violations
    execution_time_ms INTEGER                    -- How long the check took
);

CREATE INDEX idx_invariant_log_timestamp ON invariant_check_log(check_timestamp DESC);
CREATE INDEX idx_invariant_log_failed ON invariant_check_log(passed) WHERE passed = FALSE;

-- =============================================================================
-- TRIGGER: Log invariant violations on order completion
-- =============================================================================
-- When an order is marked COMPLETED, verify task accounting.

CREATE OR REPLACE FUNCTION verify_order_completion_invariants()
RETURNS TRIGGER AS $$
DECLARE
    task_delivered INTEGER;
    task_failed INTEGER;
    uses_tasks BOOLEAN;
BEGIN
    -- Only check when transitioning to COMPLETED
    IF NEW.status = 'COMPLETED' AND (OLD.status IS NULL OR OLD.status != 'COMPLETED') THEN
        uses_tasks := COALESCE(NEW.uses_task_delivery, FALSE);
        
        IF uses_tasks THEN
            -- Count delivered from COMPLETED tasks
            SELECT COALESCE(SUM(quantity), 0) INTO task_delivered
            FROM order_tasks 
            WHERE order_id = NEW.id AND status = 'COMPLETED';
            
            -- Count failed from FAILED_PERMANENT tasks
            SELECT COALESCE(SUM(quantity), 0) INTO task_failed
            FROM order_tasks 
            WHERE order_id = NEW.id AND status = 'FAILED_PERMANENT';
            
            -- Verify INV-1: quantity accounting
            IF (task_delivered + task_failed) != NEW.quantity THEN
                INSERT INTO invariant_check_log(check_type, order_id, passed, violations_count, violation_details)
                VALUES ('ORDER_COMPLETION', NEW.id, FALSE, 1, jsonb_build_object(
                    'invariant', 'INV-1',
                    'expected', NEW.quantity,
                    'task_delivered', task_delivered,
                    'task_failed', task_failed,
                    'actual_sum', task_delivered + task_failed
                ));
                
                -- Raise warning but don't block - let application handle
                RAISE WARNING 'INV-1 violation for order %: expected %, got %', 
                    NEW.id, NEW.quantity, task_delivered + task_failed;
            ELSE
                -- Log successful check
                INSERT INTO invariant_check_log(check_type, order_id, passed, violations_count)
                VALUES ('ORDER_COMPLETION', NEW.id, TRUE, 0);
            END IF;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_verify_order_completion ON orders;
CREATE TRIGGER trg_verify_order_completion
    AFTER UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION verify_order_completion_invariants();

-- =============================================================================
-- VIEW: Invariant Health Dashboard
-- =============================================================================
CREATE OR REPLACE VIEW v_invariant_health AS
SELECT 
    -- Recent violations (last 24h)
    (SELECT COUNT(*) FROM invariant_check_log 
     WHERE passed = FALSE 
     AND check_timestamp > NOW() - INTERVAL '24 hours') as violations_24h,
    
    -- Orphaned tasks (EXECUTING > 2 minutes)
    (SELECT COUNT(*) FROM order_tasks 
     WHERE status = 'EXECUTING' 
     AND execution_started_at < NOW() - INTERVAL '2 minutes') as orphaned_tasks,
    
    -- Orders with mismatched delivered count
    (SELECT COUNT(*) FROM orders o
     WHERE o.status = 'COMPLETED' 
     AND o.uses_task_delivery = TRUE
     AND o.delivered != (
         SELECT COALESCE(SUM(quantity), 0) 
         FROM order_tasks t 
         WHERE t.order_id = o.id AND t.status = 'COMPLETED'
     )) as mismatched_delivered,
    
    -- Orders COMPLETED with non-terminal tasks
    (SELECT COUNT(*) FROM orders o
     WHERE o.status = 'COMPLETED'
     AND o.uses_task_delivery = TRUE
     AND EXISTS (
         SELECT 1 FROM order_tasks t 
         WHERE t.order_id = o.id 
         AND t.status NOT IN ('COMPLETED', 'FAILED_PERMANENT')
     )) as completed_with_active_tasks,
    
    -- Total check count
    (SELECT COUNT(*) FROM invariant_check_log 
     WHERE check_timestamp > NOW() - INTERVAL '24 hours') as total_checks_24h;

COMMENT ON VIEW v_invariant_health IS 'Real-time invariant health metrics - all values should be 0';

-- =============================================================================
-- FUNCTION: Manual invariant check for an order
-- =============================================================================
CREATE OR REPLACE FUNCTION check_order_invariants(p_order_id UUID)
RETURNS TABLE(
    invariant VARCHAR(20),
    passed BOOLEAN,
    details TEXT
) AS $$
DECLARE
    v_order RECORD;
    v_completed_qty INTEGER;
    v_failed_qty INTEGER;
    v_active_count INTEGER;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id;
    
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'ORDER_NOT_FOUND'::VARCHAR, FALSE, 'Order does not exist'::TEXT;
        RETURN;
    END IF;
    
    IF COALESCE(v_order.uses_task_delivery, FALSE) THEN
        -- INV-1: Quantity accounting
        SELECT COALESCE(SUM(quantity), 0) INTO v_completed_qty
        FROM order_tasks WHERE order_id = p_order_id AND status = 'COMPLETED';
        
        SELECT COALESCE(SUM(quantity), 0) INTO v_failed_qty
        FROM order_tasks WHERE order_id = p_order_id AND status = 'FAILED_PERMANENT';
        
        IF v_order.status = 'COMPLETED' THEN
            IF (v_completed_qty + v_failed_qty) = v_order.quantity THEN
                RETURN QUERY SELECT 'INV-1'::VARCHAR, TRUE, 
                    format('Qty accounted: %s completed + %s failed = %s', v_completed_qty, v_failed_qty, v_order.quantity);
            ELSE
                RETURN QUERY SELECT 'INV-1'::VARCHAR, FALSE, 
                    format('Qty mismatch: %s completed + %s failed = %s, expected %s', 
                        v_completed_qty, v_failed_qty, v_completed_qty + v_failed_qty, v_order.quantity);
            END IF;
        ELSE
            RETURN QUERY SELECT 'INV-1'::VARCHAR, TRUE, 'Order not COMPLETED yet, skipping accounting check';
        END IF;
        
        -- INV-3: No active tasks for COMPLETED order
        IF v_order.status = 'COMPLETED' THEN
            SELECT COUNT(*) INTO v_active_count
            FROM order_tasks 
            WHERE order_id = p_order_id 
            AND status NOT IN ('COMPLETED', 'FAILED_PERMANENT');
            
            IF v_active_count = 0 THEN
                RETURN QUERY SELECT 'INV-3'::VARCHAR, TRUE, 'All tasks terminal';
            ELSE
                RETURN QUERY SELECT 'INV-3'::VARCHAR, FALSE, 
                    format('%s tasks still active for COMPLETED order', v_active_count);
            END IF;
        ELSE
            RETURN QUERY SELECT 'INV-3'::VARCHAR, TRUE, 'Order not COMPLETED yet, skipping terminal check';
        END IF;
    ELSE
        -- INV-5: No tasks for instant order
        SELECT COUNT(*) INTO v_active_count
        FROM order_tasks WHERE order_id = p_order_id;
        
        IF v_active_count = 0 THEN
            RETURN QUERY SELECT 'INV-5'::VARCHAR, TRUE, 'No tasks for instant order';
        ELSE
            RETURN QUERY SELECT 'INV-5'::VARCHAR, FALSE, 
                format('Instant order has %s tasks (should be 0)', v_active_count);
        END IF;
        
        -- INV-6: Delivered matches quantity for instant
        IF v_order.status = 'COMPLETED' THEN
            IF v_order.delivered = v_order.quantity THEN
                RETURN QUERY SELECT 'INV-6'::VARCHAR, TRUE, 
                    format('Delivered %s matches quantity', v_order.delivered);
            ELSE
                RETURN QUERY SELECT 'INV-6'::VARCHAR, FALSE, 
                    format('Delivered %s != quantity %s', v_order.delivered, v_order.quantity);
            END IF;
        ELSE
            RETURN QUERY SELECT 'INV-6'::VARCHAR, TRUE, 'Order not COMPLETED yet';
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_order_invariants IS 'Manual invariant verification for debugging';
