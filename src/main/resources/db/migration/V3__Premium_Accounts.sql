-- =============================================================================
-- Flyway Migration: V3__Premium_Accounts.sql
-- 
-- Adds premium_accounts table for Spotify bot farm management.
-- =============================================================================

-- =============================================================================
-- PREMIUM ACCOUNTS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS premium_accounts (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email                   VARCHAR(100) NOT NULL,
    password                VARCHAR(100),
    cookies                 TEXT,
    spotify_refresh_token   TEXT,
    spotify_access_token    TEXT,
    region                  VARCHAR(20) DEFAULT 'WORLDWIDE',
    premium_expiry          DATE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT premium_accounts_email_unique UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_premium_accounts_expiry ON premium_accounts(premium_expiry);
CREATE INDEX IF NOT EXISTS idx_premium_accounts_region ON premium_accounts(region);

COMMENT ON TABLE premium_accounts IS 'Spotify premium accounts for bot farm operations';
COMMENT ON COLUMN premium_accounts.cookies IS 'JSON browser cookies for session persistence';
COMMENT ON COLUMN premium_accounts.premium_expiry IS 'Date when premium subscription expires';
