-- Spotify Bot Provider - Database Schema
-- =======================================
-- PostgreSQL/Supabase compatible schema

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    track_id VARCHAR(255) NOT NULL,
    total_quantity BIGINT NOT NULL,
    delivered_quantity BIGINT DEFAULT 0,
    geo_target VARCHAR(50) NOT NULL,
    speed_tier VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_quantity CHECK (total_quantity > 0),
    CONSTRAINT chk_delivered CHECK (delivered_quantity >= 0)
);

-- Bot tasks table
CREATE TABLE IF NOT EXISTS bot_tasks (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    track_id VARCHAR(255) NOT NULL,
    target_plays INT NOT NULL,
    completed_plays INT DEFAULT 0,
    geo_target VARCHAR(50) NOT NULL,
    speed_tier VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    proxy_address VARCHAR(255),
    account_email VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Bot accounts table
CREATE TABLE IF NOT EXISTS bot_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    is_premium BOOLEAN DEFAULT TRUE,
    premium_expires DATE,
    plays_today INT DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_compromised BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Proxy pool table
CREATE TABLE IF NOT EXISTS proxy_pool (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address VARCHAR(255) UNIQUE NOT NULL,
    geo_region VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    is_residential BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    failure_count INT DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- API users table (for SMM panel integration)
CREATE TABLE IF NOT EXISTS api_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    balance DECIMAL(12, 2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_bot_tasks_order_id ON bot_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_bot_tasks_status ON bot_tasks(status);
CREATE INDEX IF NOT EXISTS idx_bot_accounts_available ON bot_accounts(is_premium, is_compromised) WHERE is_premium = TRUE AND is_compromised = FALSE;
CREATE INDEX IF NOT EXISTS idx_proxy_pool_available ON proxy_pool(geo_region, is_active) WHERE is_active = TRUE;

-- Insert sample data for development
INSERT INTO bot_accounts (email, password, is_premium) VALUES
    ('dev-account-1@spotifybot.dev', 'password123', TRUE),
    ('dev-account-2@spotifybot.dev', 'password123', TRUE),
    ('dev-account-3@spotifybot.dev', 'password123', TRUE)
ON CONFLICT (email) DO NOTHING;
