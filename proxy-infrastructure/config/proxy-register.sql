-- Phase 3a: Register local development proxy in PostgreSQL
-- Run this after starting proxy-node.sh on port 9090

-- Check if proxy_nodes table exists (it should from existing schema)
-- INSERT INTO proxy_nodes (id, name, host, port, status, tier, cost_per_1k, created_at, updated_at)
-- VALUES (
--   gen_random_uuid(),
--   'localhost-dev-1',
--   'localhost',
--   9090,
--   'ONLINE',
--   'RESIDENTIAL',
--   0.10,
--   NOW(),
--   NOW()
-- );

-- For development: Use proxies table if that's what exists
INSERT INTO proxies (id, name, host, port, status, tier, cost_per_1k, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'localhost-dev-1',
  'localhost',
  9090,
  'ONLINE',
  'RESIDENTIAL',
  0.10,
  NOW(),
  NOW()
) ON CONFLICT (name) DO UPDATE SET
  status = 'ONLINE',
  updated_at = NOW();

-- Verify registration
SELECT id, name, host, port, status, tier FROM proxies WHERE name = 'localhost-dev-1';
