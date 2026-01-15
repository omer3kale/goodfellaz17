-- ═══════════════════════════════════════════════════════════════════════════
-- GOODFELLAZ17 - Idempotency Support Migration
-- ═══════════════════════════════════════════════════════════════════════════
-- 
-- Adds unique index on external_order_id for safe retry handling.
-- Run this once on your database to enable idempotency keys.
--
-- Execute via:
--   docker exec -i goodfellaz17-postgres psql -U goodfellaz17 -d goodfellaz17 < scripts/add-idempotency-index.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- Add unique index on external_order_id (idempotency key)
-- This ensures duplicate idempotency keys are rejected at the DB level
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_external_order_id_unique 
ON orders (external_order_id) 
WHERE external_order_id IS NOT NULL;

-- Verify the index was created
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'orders' AND indexname LIKE '%external_order_id%';

-- Show sample usage
SELECT 'Idempotency index created successfully!' AS status;
SELECT 'Usage: Include "idempotencyKey": "your-unique-key" in POST /api/public/orders' AS hint;
