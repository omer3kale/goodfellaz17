-- =============================================================================
-- Flyway Migration: V4__Fix_JSONB_to_TEXT.sql
-- 
-- Changes JSONB columns to TEXT for R2DBC compatibility.
-- R2DBC doesn't have built-in JSONB type conversion, so we store JSON as TEXT.
-- =============================================================================

-- Fix proxy_nodes.tags column
ALTER TABLE proxy_nodes ALTER COLUMN tags TYPE TEXT USING tags::TEXT;

-- Fix proxy_metrics.error_codes column  
ALTER TABLE proxy_metrics ALTER COLUMN error_codes TYPE TEXT USING error_codes::TEXT;
