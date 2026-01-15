-- V5: Add estimated_completion_at to orders table for capacity planning
-- Supports the 15k package scheduler

ALTER TABLE orders 
ADD COLUMN IF NOT EXISTS estimated_completion_at TIMESTAMP;

COMMENT ON COLUMN orders.estimated_completion_at IS 'ETA calculated by CapacityService at order creation';
