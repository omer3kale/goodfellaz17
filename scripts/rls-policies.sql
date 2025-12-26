-- =========================================
-- Row-Level Security (RLS) for Neon PostgreSQL
-- Ensures customers can only see their own orders
-- =========================================

-- Enable RLS on orders table
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if any
DROP POLICY IF EXISTS customer_orders_policy ON orders;
DROP POLICY IF EXISTS admin_orders_policy ON orders;

-- Create policy: customers see only their own orders
-- The api_key must be passed via SET app.api_key = 'botzzz_xxx' before queries
CREATE POLICY customer_orders_policy ON orders
    FOR ALL
    USING (api_key = current_setting('app.api_key', true));

-- Admin bypass policy (for admin operations)
-- When app.is_admin = 'true', allow all access
CREATE POLICY admin_orders_policy ON orders
    FOR ALL
    USING (current_setting('app.is_admin', true) = 'true');

-- =========================================
-- Usage from Spring Boot R2DBC:
-- 
-- Before customer queries:
--   SET app.api_key = 'botzzz_customer_key';
--   SELECT * FROM orders; -- only sees their orders
--
-- For admin queries:
--   SET app.is_admin = 'true';
--   SELECT * FROM orders; -- sees all orders
-- =========================================

-- Grant permissions
GRANT SELECT, INSERT, UPDATE ON orders TO neondb_owner;
GRANT SELECT ON api_keys TO neondb_owner;
GRANT SELECT ON services TO neondb_owner;

-- Verify RLS is enabled
SELECT tablename, rowsecurity 
FROM pg_tables 
WHERE tablename = 'orders';
