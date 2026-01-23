-- =============================================================================
-- Flyway Migration: V13__Audit_Events.sql
-- 
-- Purpose: Immutable audit trail for compliance, debugging, and thesis evidence
-- 
-- Design Principles:
--   1. IMMUTABLE: No UPDATE/DELETE on audit_events (append-only)
--   2. COMPLETE: Captures who, what, when, why, before, after
--   3. QUERYABLE: Indexed for fast resource-specific queries
--   4. AUTOMATED: Triggers capture changes without application code
--
-- Thesis Relevance:
--   "Full audit trails prove data integrity and enable post-mortem analysis
--    of bot behavior, proxy failures, and financial transactions."
--
-- @author RWTH Research Project
-- @version 1.0.0
-- =============================================================================

-- =============================================================================
-- AUDIT EVENTS TABLE
-- =============================================================================
-- Immutable log of all significant changes across the platform.
-- Think of it as a blockchain of changes - append-only, never modified.
-- =============================================================================
CREATE TABLE audit_events (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- When
    event_timestamp         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Who
    actor_type              VARCHAR(50) NOT NULL,       -- 'USER', 'SYSTEM', 'ADMIN', 'BOT', 'TRIGGER'
    actor_id                UUID,                       -- User ID if actor_type = 'USER'
    actor_name              VARCHAR(255),               -- Display name for logs
    actor_ip                INET,                       -- IP address if available
    
    -- What
    action                  VARCHAR(100) NOT NULL,      -- 'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', etc.
    resource_type           VARCHAR(100) NOT NULL,      -- 'orders', 'users', 'proxy_nodes', etc.
    resource_id             UUID NOT NULL,              -- Primary key of affected resource
    resource_name           VARCHAR(255),               -- Human-readable identifier
    
    -- Details
    old_value               JSONB,                      -- State before change
    new_value               JSONB,                      -- State after change
    changed_fields          TEXT[],                     -- Array of field names that changed
    
    -- Context
    reason                  VARCHAR(500),               -- Why this change was made
    correlation_id          UUID,                       -- Links related events (e.g., order + tasks)
    tenant_code             VARCHAR(50),                -- Multi-tenant context
    
    -- Metadata
    source                  VARCHAR(100) NOT NULL DEFAULT 'application',  -- 'trigger', 'api', 'admin_panel', etc.
    session_id              VARCHAR(100),               -- User session if applicable
    request_path            VARCHAR(500),               -- API endpoint that triggered this
    
    -- Index-friendly timestamp partition
    event_date              DATE NOT NULL DEFAULT CURRENT_DATE
);

-- Primary query pattern: "Show me all changes to resource X"
CREATE INDEX idx_audit_events_resource ON audit_events(resource_type, resource_id, event_timestamp DESC);

-- Secondary: "Show me all actions by user Y"
CREATE INDEX idx_audit_events_actor ON audit_events(actor_id, event_timestamp DESC) WHERE actor_id IS NOT NULL;

-- Tertiary: "Show me all events in correlation group"
CREATE INDEX idx_audit_events_correlation ON audit_events(correlation_id) WHERE correlation_id IS NOT NULL;

-- Partition-ready: Date-based queries
CREATE INDEX idx_audit_events_date ON audit_events(event_date, event_timestamp DESC);

-- Tenant isolation
CREATE INDEX idx_audit_events_tenant ON audit_events(tenant_code, event_timestamp DESC) WHERE tenant_code IS NOT NULL;

-- Action-specific queries
CREATE INDEX idx_audit_events_action ON audit_events(action, resource_type, event_timestamp DESC);

COMMENT ON TABLE audit_events IS 'Immutable audit trail - append only, never update or delete';
COMMENT ON COLUMN audit_events.changed_fields IS 'Array of field names that changed, for quick filtering';
COMMENT ON COLUMN audit_events.correlation_id IS 'Groups related events (e.g., order creation triggers task creation)';

-- =============================================================================
-- AUDIT TRAIL HELPER FUNCTIONS
-- =============================================================================

-- Function: Insert audit event (called by triggers)
CREATE OR REPLACE FUNCTION audit_log(
    p_actor_type VARCHAR,
    p_actor_id UUID,
    p_actor_name VARCHAR,
    p_action VARCHAR,
    p_resource_type VARCHAR,
    p_resource_id UUID,
    p_resource_name VARCHAR,
    p_old_value JSONB,
    p_new_value JSONB,
    p_reason VARCHAR DEFAULT NULL,
    p_correlation_id UUID DEFAULT NULL,
    p_source VARCHAR DEFAULT 'trigger'
) RETURNS UUID AS $$
DECLARE
    v_audit_id UUID;
    v_changed_fields TEXT[];
BEGIN
    -- Calculate changed fields
    IF p_old_value IS NOT NULL AND p_new_value IS NOT NULL THEN
        SELECT ARRAY_AGG(key)
        INTO v_changed_fields
        FROM (
            SELECT key FROM jsonb_object_keys(p_old_value) AS key
            WHERE p_old_value->key IS DISTINCT FROM p_new_value->key
            UNION
            SELECT key FROM jsonb_object_keys(p_new_value) AS key
            WHERE p_old_value->key IS DISTINCT FROM p_new_value->key
        ) changed;
    END IF;
    
    INSERT INTO audit_events (
        actor_type, actor_id, actor_name,
        action, resource_type, resource_id, resource_name,
        old_value, new_value, changed_fields,
        reason, correlation_id, source
    ) VALUES (
        p_actor_type, p_actor_id, p_actor_name,
        p_action, p_resource_type, p_resource_id, p_resource_name,
        p_old_value, p_new_value, v_changed_fields,
        p_reason, p_correlation_id, p_source
    )
    RETURNING id INTO v_audit_id;
    
    RETURN v_audit_id;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- TRIGGER: proxy_nodes audit
-- =============================================================================
-- Tracks: status changes, tier changes, health state transitions
-- Use case: Debug "who marked this proxy as OFFLINE?"
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_audit_proxy_nodes()
RETURNS TRIGGER AS $$
DECLARE
    v_action VARCHAR;
    v_old_json JSONB;
    v_new_json JSONB;
    v_reason VARCHAR;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_action := 'CREATE';
        v_new_json := to_jsonb(NEW);
        v_reason := 'Proxy node registered';
    ELSIF TG_OP = 'UPDATE' THEN
        v_action := 'UPDATE';
        v_old_json := to_jsonb(OLD);
        v_new_json := to_jsonb(NEW);
        
        -- Specific action names for important changes
        IF OLD.status IS DISTINCT FROM NEW.status THEN
            v_action := 'STATUS_CHANGE';
            v_reason := format('Status: %s → %s', OLD.status, NEW.status);
        ELSIF OLD.health_state IS DISTINCT FROM NEW.health_state THEN
            v_action := 'HEALTH_STATE_CHANGE';
            v_reason := format('Health: %s → %s', OLD.health_state, NEW.health_state);
        ELSIF OLD.tier IS DISTINCT FROM NEW.tier THEN
            v_action := 'TIER_CHANGE';
            v_reason := format('Tier: %s → %s', OLD.tier, NEW.tier);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        v_action := 'DELETE';
        v_old_json := to_jsonb(OLD);
        v_reason := 'Proxy node removed';
    END IF;
    
    PERFORM audit_log(
        'TRIGGER',
        NULL,
        'proxy_nodes_trigger',
        v_action,
        'proxy_nodes',
        COALESCE(NEW.id, OLD.id),
        COALESCE(NEW.ip_address, OLD.ip_address),
        v_old_json,
        v_new_json,
        v_reason,
        NULL,
        'trigger'
    );
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_proxy_nodes_trigger
    AFTER INSERT OR UPDATE OR DELETE ON proxy_nodes
    FOR EACH ROW EXECUTE FUNCTION trg_audit_proxy_nodes();

-- =============================================================================
-- TRIGGER: users audit (balance changes)
-- =============================================================================
-- Tracks: balance changes, tier changes, status changes
-- Use case: "Why did user X's balance decrease?" → admin refund at 2:34 PM
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_audit_users()
RETURNS TRIGGER AS $$
DECLARE
    v_action VARCHAR;
    v_old_json JSONB;
    v_new_json JSONB;
    v_reason VARCHAR;
BEGIN
    -- Only audit significant changes, not every login timestamp update
    IF TG_OP = 'UPDATE' THEN
        -- Skip if only last_login changed
        IF OLD.balance = NEW.balance 
           AND OLD.tier = NEW.tier 
           AND OLD.status = NEW.status
           AND OLD.email = NEW.email THEN
            RETURN NEW;
        END IF;
        
        v_action := 'UPDATE';
        v_old_json := jsonb_build_object(
            'balance', OLD.balance,
            'tier', OLD.tier,
            'status', OLD.status,
            'email', OLD.email
        );
        v_new_json := jsonb_build_object(
            'balance', NEW.balance,
            'tier', NEW.tier,
            'status', NEW.status,
            'email', NEW.email
        );
        
        -- Specific action names
        IF OLD.balance IS DISTINCT FROM NEW.balance THEN
            v_action := 'BALANCE_CHANGE';
            v_reason := format('Balance: $%.2f → $%.2f (Δ $%.2f)', 
                OLD.balance, NEW.balance, NEW.balance - OLD.balance);
        ELSIF OLD.tier IS DISTINCT FROM NEW.tier THEN
            v_action := 'TIER_CHANGE';
            v_reason := format('Tier: %s → %s', OLD.tier, NEW.tier);
        ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
            v_action := 'STATUS_CHANGE';
            v_reason := format('Status: %s → %s', OLD.status, NEW.status);
        END IF;
    ELSIF TG_OP = 'INSERT' THEN
        v_action := 'CREATE';
        v_new_json := jsonb_build_object(
            'email', NEW.email,
            'tier', NEW.tier,
            'balance', NEW.balance
        );
        v_reason := 'User registered';
    ELSIF TG_OP = 'DELETE' THEN
        v_action := 'DELETE';
        v_old_json := jsonb_build_object('email', OLD.email);
        v_reason := 'User deleted';
    END IF;
    
    PERFORM audit_log(
        'TRIGGER',
        NULL,
        'users_trigger',
        v_action,
        'users',
        COALESCE(NEW.id, OLD.id),
        COALESCE(NEW.email, OLD.email),
        v_old_json,
        v_new_json,
        v_reason,
        NULL,
        'trigger'
    );
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_users_trigger
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION trg_audit_users();

-- =============================================================================
-- TRIGGER: orders audit
-- =============================================================================
-- Tracks: status transitions, quantity changes, refunds
-- Use case: Full order lifecycle: PENDING → PROCESSING → PARTIAL → COMPLETED
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_audit_orders()
RETURNS TRIGGER AS $$
DECLARE
    v_action VARCHAR;
    v_old_json JSONB;
    v_new_json JSONB;
    v_reason VARCHAR;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_action := 'CREATE';
        v_new_json := jsonb_build_object(
            'status', NEW.status,
            'quantity', NEW.quantity,
            'amount', NEW.amount,
            'service_id', NEW.service_id,
            'user_id', NEW.user_id
        );
        v_reason := format('Order created: %s units for $%.2f', NEW.quantity, NEW.amount);
    ELSIF TG_OP = 'UPDATE' THEN
        v_old_json := jsonb_build_object(
            'status', OLD.status,
            'quantity', OLD.quantity,
            'delivered', OLD.delivered,
            'remains', OLD.remains
        );
        v_new_json := jsonb_build_object(
            'status', NEW.status,
            'quantity', NEW.quantity,
            'delivered', NEW.delivered,
            'remains', NEW.remains
        );
        
        IF OLD.status IS DISTINCT FROM NEW.status THEN
            v_action := 'STATUS_CHANGE';
            v_reason := format('Status: %s → %s', OLD.status, NEW.status);
        ELSIF OLD.delivered IS DISTINCT FROM NEW.delivered THEN
            v_action := 'DELIVERY_UPDATE';
            v_reason := format('Delivered: %s → %s (+%s)', 
                OLD.delivered, NEW.delivered, NEW.delivered - OLD.delivered);
        ELSE
            v_action := 'UPDATE';
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        v_action := 'DELETE';
        v_old_json := to_jsonb(OLD);
        v_reason := 'Order deleted';
    END IF;
    
    PERFORM audit_log(
        'TRIGGER',
        NEW.user_id,  -- Actor is the order owner
        NULL,
        v_action,
        'orders',
        COALESCE(NEW.id, OLD.id),
        COALESCE(NEW.link, OLD.link),
        v_old_json,
        v_new_json,
        v_reason,
        COALESCE(NEW.id, OLD.id),  -- correlation_id = order_id for grouping
        'trigger'
    );
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_orders_trigger
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW EXECUTE FUNCTION trg_audit_orders();

-- =============================================================================
-- TRIGGER: balance_transactions audit
-- =============================================================================
-- All financial transactions are logged (deposits, charges, refunds)
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_audit_balance_transactions()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM audit_log(
            'TRIGGER',
            NEW.user_id,
            NULL,
            NEW.transaction_type,  -- 'DEPOSIT', 'ORDER_CHARGE', 'REFUND', etc.
            'balance_transactions',
            NEW.id,
            format('%s: $%.2f', NEW.transaction_type, NEW.amount),
            NULL,
            jsonb_build_object(
                'amount', NEW.amount,
                'balance_before', NEW.balance_before,
                'balance_after', NEW.balance_after,
                'reference_type', NEW.reference_type,
                'reference_id', NEW.reference_id
            ),
            format('%s transaction: $%.2f', NEW.transaction_type, NEW.amount),
            NEW.reference_id,  -- correlation_id = order_id for charges/refunds
            'trigger'
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_balance_transactions_trigger
    AFTER INSERT ON balance_transactions
    FOR EACH ROW EXECUTE FUNCTION trg_audit_balance_transactions();

-- =============================================================================
-- API HELPER: Get audit trail for a resource
-- =============================================================================
-- Usage: SELECT * FROM get_audit_trail('orders', '<order-uuid>');
-- =============================================================================
CREATE OR REPLACE FUNCTION get_audit_trail(
    p_resource_type VARCHAR,
    p_resource_id UUID,
    p_limit INTEGER DEFAULT 100
)
RETURNS TABLE (
    event_timestamp TIMESTAMP WITH TIME ZONE,
    action VARCHAR,
    actor_type VARCHAR,
    actor_name VARCHAR,
    old_value JSONB,
    new_value JSONB,
    changed_fields TEXT[],
    reason VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ae.event_timestamp,
        ae.action,
        ae.actor_type,
        ae.actor_name,
        ae.old_value,
        ae.new_value,
        ae.changed_fields,
        ae.reason
    FROM audit_events ae
    WHERE ae.resource_type = p_resource_type
      AND ae.resource_id = p_resource_id
    ORDER BY ae.event_timestamp DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_audit_trail IS 'Returns full audit history for a specific resource';

-- =============================================================================
-- RETENTION: Prevent modifications to audit_events
-- =============================================================================
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events table is immutable. UPDATE and DELETE are not allowed.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
