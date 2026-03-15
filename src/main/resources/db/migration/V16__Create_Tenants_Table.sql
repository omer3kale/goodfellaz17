-- Flyway Migration: V16__Create_Tenants_Table.sql
-- Create tenants table for multi-tenant isolation.
-- FTL-B Phase 2: Reference table supporting order.tenant_id FK.

CREATE TABLE tenants (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    
    -- Tenant identifier: API key prefix or company code
    code NVARCHAR(64) NOT NULL UNIQUE,
    
    -- Human-readable name
    name NVARCHAR(256) NOT NULL,
    
    -- Contact/admin info (optional)
    contact_email NVARCHAR(256),
    
    -- Billing/account info
    active BIT NOT NULL DEFAULT 1,
    credit_balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    
    -- Temporal tracking
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

-- Indexes for common queries
CREATE INDEX IX_tenants_code ON tenants(code);
CREATE INDEX IX_tenants_active ON tenants(active);
CREATE INDEX IX_tenants_created_at ON tenants(created_at DESC);

-- Constraints
ALTER TABLE tenants ADD CONSTRAINT CK_tenants_code_not_empty CHECK (LEN(TRIM(code)) > 0);
ALTER TABLE tenants ADD CONSTRAINT CK_tenants_name_not_empty CHECK (LEN(TRIM(name)) > 0);
