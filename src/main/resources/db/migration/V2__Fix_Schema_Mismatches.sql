-- =============================================================================
-- Flyway Migration: V2__Fix_Schema_Mismatches.sql
-- 
-- Fixes DDL/Entity mismatches discovered during code review:
-- 1. Add missing columns to orders table
-- 2. Fix geo_profile CHECK constraint to match GeoProfile enum
-- 3. Add missing indexes for common query patterns
-- 4. Fix status CHECK constraint on users table
-- =============================================================================

-- =============================================================================
-- ORDERS TABLE FIXES
-- =============================================================================

-- Add missing service_name column (denormalized for performance)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_name VARCHAR(255);

-- Add missing price_per_unit column
ALTER TABLE orders ADD COLUMN IF NOT EXISTS price_per_unit DECIMAL(10,4);

-- Add missing remains column (quantity - delivered)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS remains INTEGER NOT NULL DEFAULT 0;

-- Rename cost to total_cost for clarity (if not exists, add it)
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'orders' AND column_name = 'total_cost') THEN
        ALTER TABLE orders RENAME COLUMN cost TO total_cost;
    END IF;
END $$;

-- Update remains for existing orders
UPDATE orders SET remains = quantity - delivered WHERE remains = 0 AND quantity > delivered;

-- Drop old geo_profile constraint and add corrected one
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_geo_profile_check;
ALTER TABLE orders ADD CONSTRAINT orders_geo_profile_check CHECK (geo_profile IN (
    'WORLDWIDE', 'USA', 'UK', 'DE', 'FR', 'BR', 'MX', 
    'LATAM', 'EUROPE', 'ASIA', 'PREMIUM_MIX'
));

-- Drop old status constraint and add corrected one
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (
    'PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED', 
    'PARTIAL', 'FAILED', 'REFUNDED', 'CANCELLED'
));

-- =============================================================================
-- USERS TABLE FIXES
-- =============================================================================

-- Fix status check to include all UserStatus enum values
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN (
    'PENDING', 'ACTIVE', 'SUSPENDED', 'BANNED', 'PENDING_VERIFICATION'
));

-- =============================================================================
-- MISSING INDEXES
-- =============================================================================

-- Index for time-range queries on orders
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);

-- Index for user_id queries on orders (if not exists)
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);

-- Index for time-range queries on balance_transactions
CREATE INDEX IF NOT EXISTS idx_balance_tx_timestamp ON balance_transactions(timestamp);

-- Composite index for user order history queries
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at DESC);

-- Index for service lookups
CREATE INDEX IF NOT EXISTS idx_services_is_active ON services(is_active);

-- =============================================================================
-- COMMENTS
-- =============================================================================
COMMENT ON COLUMN orders.service_name IS 'Denormalized service name for display (avoid JOIN)';
COMMENT ON COLUMN orders.price_per_unit IS 'Price per unit at time of order (for historical accuracy)';
COMMENT ON COLUMN orders.remains IS 'Remaining quantity to deliver (quantity - delivered)';
COMMENT ON COLUMN orders.total_cost IS 'Total order cost (quantity * price_per_unit)';
