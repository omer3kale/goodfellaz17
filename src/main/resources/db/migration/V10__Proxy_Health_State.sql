-- =============================================================================
-- V10: Proxy Health State Enhancement
-- =============================================================================
-- Purpose: Add explicit health_state column for selection algorithm.
--          Separates operational status (ONLINE/OFFLINE) from health scoring.
--
-- Health State Model (from architecture spec):
--   HEALTHY   - successRate >= 0.85, preferred for task assignment
--   DEGRADED  - successRate >= 0.70 && < 0.85, fallback only (logged)
--   OFFLINE   - successRate < 0.70 OR operational issues, never selected
--
-- The existing 'status' column tracks operational state (ONLINE, MAINTENANCE, BANNED).
-- The new 'health_state' column tracks performance health for selection decisions.
-- =============================================================================

-- Add health_state column with default HEALTHY
ALTER TABLE proxy_nodes 
ADD COLUMN IF NOT EXISTS health_state VARCHAR(20) NOT NULL DEFAULT 'HEALTHY';

-- Add constraint for valid health states
ALTER TABLE proxy_nodes
ADD CONSTRAINT proxy_nodes_health_state_check 
CHECK (health_state IN ('HEALTHY', 'DEGRADED', 'OFFLINE'));

-- Create index for health-based selection queries
CREATE INDEX IF NOT EXISTS idx_proxy_nodes_health_selection 
ON proxy_nodes(tier, status, health_state, current_load) 
WHERE status = 'ONLINE' AND health_state != 'OFFLINE';

-- =============================================================================
-- Health State Update Trigger
-- =============================================================================
-- Automatically compute health_state from proxy_metrics.success_rate
-- Runs on INSERT/UPDATE to proxy_metrics

CREATE OR REPLACE FUNCTION update_proxy_health_state()
RETURNS TRIGGER AS $$
DECLARE
    v_success_rate DOUBLE PRECISION;
    v_new_state VARCHAR(20);
BEGIN
    v_success_rate := NEW.success_rate;
    
    -- Determine health state based on success rate thresholds
    IF v_success_rate >= 0.85 THEN
        v_new_state := 'HEALTHY';
    ELSIF v_success_rate >= 0.70 THEN
        v_new_state := 'DEGRADED';
    ELSE
        v_new_state := 'OFFLINE';
    END IF;
    
    -- Update the proxy node's health state
    UPDATE proxy_nodes 
    SET health_state = v_new_state
    WHERE id = NEW.proxy_node_id;
    
    -- Log state transitions for monitoring
    IF TG_OP = 'UPDATE' AND OLD.success_rate IS DISTINCT FROM NEW.success_rate THEN
        RAISE NOTICE 'PROXY_HEALTH_CHANGE | nodeId=% | oldRate=% | newRate=% | newState=%',
            NEW.proxy_node_id, OLD.success_rate, NEW.success_rate, v_new_state;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_proxy_health_state
AFTER INSERT OR UPDATE OF success_rate ON proxy_metrics
FOR EACH ROW
EXECUTE FUNCTION update_proxy_health_state();

-- =============================================================================
-- Initial Health State Population
-- =============================================================================
-- Set health_state based on current metrics for existing nodes

UPDATE proxy_nodes pn
SET health_state = CASE 
    WHEN pm.success_rate >= 0.85 THEN 'HEALTHY'
    WHEN pm.success_rate >= 0.70 THEN 'DEGRADED'
    ELSE 'OFFLINE'
END
FROM proxy_metrics pm
WHERE pn.id = pm.proxy_node_id;

-- Nodes without metrics default to HEALTHY (will be updated after first request)

-- =============================================================================
-- View for Phase 1 Selection Algorithm
-- =============================================================================
-- Provides a unified view joining nodes with metrics for selection queries

CREATE OR REPLACE VIEW v_proxy_selection AS
SELECT 
    pn.id,
    pn.provider,
    pn.public_ip,
    pn.port,
    pn.region,
    pn.country,
    pn.city,
    pn.tier,
    pn.capacity,
    pn.current_load,
    pn.cost_per_hour,
    pn.auth_username,
    pn.auth_password,
    pn.status,
    pn.health_state,
    COALESCE(pm.success_rate, 1.0) AS success_rate,
    COALESCE(pm.ban_rate, 0.0) AS ban_rate,
    COALESCE(pm.latency_p50, 0) AS latency_p50,
    COALESCE(pm.latency_p95, 0) AS latency_p95,
    COALESCE(pm.total_requests, 0) AS total_requests,
    COALESCE(pm.active_connections, 0) AS active_connections,
    -- Compute load percentage for selection
    CASE 
        WHEN pn.capacity > 0 
        THEN (pn.current_load::DOUBLE PRECISION / pn.capacity) * 100 
        ELSE 100 
    END AS load_percent,
    -- Selection score (higher is better)
    -- Formula: successRate * (1 - loadPercent/100) * tierBonus
    COALESCE(pm.success_rate, 1.0) 
        * (1 - (pn.current_load::DOUBLE PRECISION / GREATEST(pn.capacity, 1)) * 0.3)
        * CASE pn.tier 
            WHEN 'MOBILE' THEN 1.3      -- Best quality, prefer
            WHEN 'RESIDENTIAL' THEN 1.2 -- High quality
            WHEN 'ISP' THEN 1.1         -- Good quality
            WHEN 'DATACENTER' THEN 1.0  -- Base tier
            ELSE 0.9                     -- Unknown tier penalty
          END AS selection_score
FROM proxy_nodes pn
LEFT JOIN proxy_metrics pm ON pn.id = pm.proxy_node_id;

COMMENT ON VIEW v_proxy_selection IS 'Unified view for proxy selection with computed scores';

-- =============================================================================
-- Add helper function for selection with tier preference
-- =============================================================================
-- Selection rules from architecture spec:
-- 1. Filter: status=ONLINE, health_state IN (HEALTHY, DEGRADED)
-- 2. Prefer: health_state=HEALTHY (successRate >= 0.85)
-- 3. Tier preference: MOBILE > RESIDENTIAL > ISP > DATACENTER
-- 4. Fallback: health_state=DEGRADED with logging
-- 5. Never: health_state=OFFLINE

CREATE OR REPLACE FUNCTION select_best_proxy(
    p_target_country VARCHAR DEFAULT NULL,
    p_min_success_rate DOUBLE PRECISION DEFAULT 0.70
)
RETURNS TABLE (
    proxy_id UUID,
    proxy_ip VARCHAR,
    proxy_port INTEGER,
    tier VARCHAR,
    health_state VARCHAR,
    success_rate DOUBLE PRECISION,
    load_percent DOUBLE PRECISION,
    is_degraded BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    WITH ranked_proxies AS (
        SELECT 
            v.id,
            v.public_ip,
            v.port,
            v.tier,
            v.health_state,
            v.success_rate,
            v.load_percent,
            v.health_state = 'DEGRADED' AS is_degraded_flag,
            -- Tier rank: MOBILE=1, RESIDENTIAL=2, ISP=3, DATACENTER=4
            CASE v.tier
                WHEN 'MOBILE' THEN 1
                WHEN 'RESIDENTIAL' THEN 2
                WHEN 'ISP' THEN 3
                WHEN 'DATACENTER' THEN 4
                ELSE 5
            END AS tier_rank,
            -- Health rank: HEALTHY=1, DEGRADED=2
            CASE v.health_state
                WHEN 'HEALTHY' THEN 1
                WHEN 'DEGRADED' THEN 2
                ELSE 3
            END AS health_rank
        FROM v_proxy_selection v
        WHERE v.status = 'ONLINE'
          AND v.health_state IN ('HEALTHY', 'DEGRADED')
          AND v.success_rate >= p_min_success_rate
          AND (p_target_country IS NULL OR v.country = p_target_country)
    )
    SELECT 
        r.id,
        r.public_ip,
        r.port,
        r.tier,
        r.health_state,
        r.success_rate,
        r.load_percent,
        r.is_degraded_flag
    FROM ranked_proxies r
    ORDER BY 
        r.health_rank ASC,     -- Prefer HEALTHY over DEGRADED
        r.tier_rank ASC,       -- Prefer MOBILE > RESIDENTIAL > ISP > DATACENTER
        r.load_percent ASC     -- Prefer lowest load
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION select_best_proxy IS 'Select best available proxy per architecture spec';
