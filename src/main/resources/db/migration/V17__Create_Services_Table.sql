-- Flyway Migration: V17__Create_Services_Table.sql
-- Create services table for delivery service types (PLAYS, FOLLOWERS, etc.).
-- FTL-B Phase 2: Reference table supporting order.service_id FK.

CREATE TABLE services (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Service code: SPOTIFY_PLAYS, SPOTIFY_FOLLOWERS, TIKTOK_VIEWS, etc.
    code NVARCHAR(64) NOT NULL UNIQUE,
    
    -- Human-readable name
    name NVARCHAR(256) NOT NULL,
    
    -- Service description
    description NVARCHAR(MAX),
    
    -- Is this service active for new orders?
    active BIT NOT NULL DEFAULT 1,
    
    -- Min and max quantity constraints per order
    min_quantity INT NOT NULL DEFAULT 1,
    max_quantity INT NOT NULL DEFAULT 1000000,
    
    -- Temporal tracking
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

-- Indexes for common queries
CREATE INDEX IX_services_code ON services(code);
CREATE INDEX IX_services_active ON services(active);
CREATE INDEX IX_services_created_at ON services(created_at DESC);

-- Constraints
ALTER TABLE services ADD CONSTRAINT CK_services_code_not_empty CHECK (LEN(TRIM(code)) > 0);
ALTER TABLE services ADD CONSTRAINT CK_services_name_not_empty CHECK (LEN(TRIM(name)) > 0);
ALTER TABLE services ADD CONSTRAINT CK_services_min_lte_max CHECK (min_quantity <= max_quantity);
ALTER TABLE services ADD CONSTRAINT CK_services_quantities_positive CHECK (min_quantity > 0 AND max_quantity > 0);
