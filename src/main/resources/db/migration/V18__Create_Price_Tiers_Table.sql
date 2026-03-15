-- Flyway Migration: V18__Create_Price_Tiers_Table.sql
-- Create price_tiers table for tiered pricing model.
-- FTL-B Phase 2: Reference table supporting order.price_tier_id FK.
-- Pricing typically: 0.13 USD per 1,000 plays, 0.9 USD per 1,000 followers, etc.

CREATE TABLE price_tiers (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Foreign key to services table
    service_id UNIQUEIDENTIFIER NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    
    -- Tier name: "Standard", "Bulk", "Premium", etc.
    tier_name NVARCHAR(64) NOT NULL,
    
    -- Unit cost: cost per quantity unit (e.g., 0.13 USD per 1,000 plays)
    unit_cost DECIMAL(10, 6) NOT NULL,
    
    -- Quantity multiplier: 1 for per-unit, 1000 for per-1000 (plays), etc.
    quantity_step INT NOT NULL DEFAULT 1,
    
    -- Min/max quantity for this tier to apply
    min_quantity INT NOT NULL DEFAULT 1,
    max_quantity INT NOT NULL DEFAULT 1000000,
    
    -- Is this tier active?
    active BIT NOT NULL DEFAULT 1,
    
    -- Temporal tracking
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

-- Indexes for common queries
CREATE INDEX IX_price_tiers_service_id ON price_tiers(service_id);
CREATE INDEX IX_price_tiers_service_active ON price_tiers(service_id, active);
CREATE INDEX IX_price_tiers_created_at ON price_tiers(created_at DESC);

-- Constraints
ALTER TABLE price_tiers ADD CONSTRAINT CK_price_tiers_cost_positive CHECK (unit_cost > 0);
ALTER TABLE price_tiers ADD CONSTRAINT CK_price_tiers_step_positive CHECK (quantity_step > 0);
ALTER TABLE price_tiers ADD CONSTRAINT CK_price_tiers_min_lte_max CHECK (min_quantity <= max_quantity);
ALTER TABLE price_tiers ADD CONSTRAINT CK_price_tiers_tier_name_not_empty CHECK (LEN(TRIM(tier_name)) > 0);
