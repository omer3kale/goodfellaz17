-- GOODFELLAZ17 / botzzz773.pro - Database Schema
-- ==============================================
-- PostgreSQL/Neon compatible schema
-- Exact field match for customer dashboard + admin panel

-- ==================== USERS (Platform users) ====================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tier VARCHAR(20) DEFAULT 'CONSUMER',       -- CONSUMER, RESELLER, AGENCY
    balance DECIMAL(10,2) DEFAULT 0.00,
    api_key VARCHAR(64) UNIQUE,
    webhook_url VARCHAR(512),
    discord_webhook VARCHAR(512),
    company_name VARCHAR(255),
    referral_code VARCHAR(32),
    referred_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(30) DEFAULT 'ACTIVE',       -- ACTIVE, SUSPENDED, PENDING_VERIFICATION
    email_verified BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_api_key ON users(api_key);

-- ==================== API KEYS (Customer wallets) ====================
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(64) UNIQUE NOT NULL,       -- "demo_abc123xyz"
    user_email VARCHAR(255),
    user_name VARCHAR(100),                    -- Display name
    balance DECIMAL(10,2) DEFAULT 0.00,        -- $2347.50
    total_spent DECIMAL(10,2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ==================== SERVICES (12 packages) ====================
CREATE TABLE IF NOT EXISTS services (
    id SERIAL PRIMARY KEY,
    service_id VARCHAR(50) UNIQUE NOT NULL,    -- "lightning_12h"
    name VARCHAR(100) NOT NULL,                -- "Lightning 12h"
    category VARCHAR(50) NOT NULL,             -- "plays", "listeners", "followers"
    price_per_1000 DECIMAL(8,4) NOT NULL,      -- 1.9900
    delivery_hours INTEGER NOT NULL,           -- 12
    min_quantity INTEGER DEFAULT 1000,
    max_quantity INTEGER DEFAULT 1000000,
    neon_color VARCHAR(7) DEFAULT '#00ff88',   -- "#00ff88"
    speed_tier VARCHAR(20) DEFAULT 'STANDARD', -- LIGHTNING, STEALTH, ULTRA
    geo_target VARCHAR(20) DEFAULT 'WORLDWIDE',
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ==================== ORDERS (Core tracking) ====================
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(64) REFERENCES api_keys(api_key),
    service_id INTEGER REFERENCES services(id),
    link VARCHAR(500) NOT NULL,                -- Spotify track/playlist URL
    quantity INTEGER NOT NULL,
    charged DECIMAL(10,2) NOT NULL,            -- $19.90
    status VARCHAR(20) DEFAULT 'Pending',      -- Pending|Processing|Completed|Failed|Refunded
    progress INTEGER DEFAULT 0,                -- 0-100%
    delivered_quantity INTEGER DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    refundable BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_quantity CHECK (quantity > 0),
    CONSTRAINT chk_progress CHECK (progress >= 0 AND progress <= 100)
);

-- ==================== BOT TASKS (Execution tracking) ====================
CREATE TABLE IF NOT EXISTS bot_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    track_id VARCHAR(255) NOT NULL,
    target_plays INTEGER NOT NULL,
    completed_plays INTEGER DEFAULT 0,
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

-- ==================== ORDER TASKS (15k engine task units) ====================
CREATE TABLE IF NOT EXISTS order_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    last_error TEXT,
    proxy_node_id UUID,
    execution_started_at TIMESTAMP WITH TIME ZONE,
    executed_at TIMESTAMP WITH TIME ZONE,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_after TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    idempotency_token VARCHAR(128) NOT NULL,
    worker_id VARCHAR(64),
    
    CONSTRAINT uk_order_task_idempotency UNIQUE (idempotency_token),
    CONSTRAINT uk_order_sequence UNIQUE (order_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_order_tasks_order_id ON order_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_order_tasks_status ON order_tasks(status);
CREATE INDEX IF NOT EXISTS idx_order_tasks_scheduled ON order_tasks(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_order_tasks_worker ON order_tasks(worker_id);

-- ==================== BOT ACCOUNTS ====================
CREATE TABLE IF NOT EXISTS bot_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_premium BOOLEAN DEFAULT TRUE,
    premium_expires DATE,
    plays_today INTEGER DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_compromised BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ==================== PREMIUM ACCOUNTS (OAuth farm) ====================
CREATE TABLE IF NOT EXISTS premium_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255),
    cookies TEXT,
    premium_expiry DATE NOT NULL,
    region VARCHAR(50) DEFAULT 'WORLDWIDE',
    spotify_refresh_token TEXT,
    spotify_access_token TEXT,
    plays_today INTEGER DEFAULT 0,
    last_play_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_premium_accounts_expiry ON premium_accounts(premium_expiry);
CREATE INDEX IF NOT EXISTS idx_premium_accounts_region ON premium_accounts(region);

-- ==================== PROXY POOL ====================
CREATE TABLE IF NOT EXISTS proxy_pool (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address VARCHAR(255) UNIQUE NOT NULL,
    geo_region VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    is_residential BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    failure_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ==================== TRANSACTIONS (Payment history) ====================
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(64) REFERENCES api_keys(api_key),
    type VARCHAR(20) NOT NULL,                 -- 'deposit', 'charge', 'refund'
    amount DECIMAL(10,2) NOT NULL,
    balance_after DECIMAL(10,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ==================== INDEXES ====================
CREATE INDEX IF NOT EXISTS idx_orders_api_key ON orders(api_key);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_updated_at ON orders(updated_at);
CREATE INDEX IF NOT EXISTS idx_bot_tasks_order_id ON bot_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_bot_tasks_status ON bot_tasks(status);
CREATE INDEX IF NOT EXISTS idx_api_keys_api_key ON api_keys(api_key);
CREATE INDEX IF NOT EXISTS idx_services_category ON services(category);
CREATE INDEX IF NOT EXISTS idx_services_is_active ON services(is_active);
CREATE INDEX IF NOT EXISTS idx_transactions_api_key ON transactions(api_key);

-- ==================== SEED: 12 SERVICES ====================
INSERT INTO services (service_id, name, category, price_per_1000, delivery_hours, min_quantity, max_quantity, neon_color, speed_tier, geo_target, description) VALUES
-- LIGHTNING TIER (12h, cheapest)
('lightning_12h', 'Lightning 12h', 'plays', 1.99, 12, 1000, 1000000, '#00ff88', 'LIGHTNING', 'WORLDWIDE', 'Fastest delivery - 12 hour completion'),
('lightning_usa', 'Lightning USA', 'plays', 2.99, 12, 1000, 500000, '#00ff88', 'LIGHTNING', 'USA', 'USA-targeted lightning plays'),

-- STEALTH TIER (48h, drip scheduled)
('stealth_48h', 'Stealth 48h', 'plays', 2.49, 48, 1000, 1000000, '#00d4ff', 'STEALTH', 'WORLDWIDE', 'Drip-scheduled organic pattern'),
('stealth_premium', 'Stealth Premium', 'plays', 3.49, 48, 500, 500000, '#00d4ff', 'STEALTH', 'WORLDWIDE', 'Premium accounts only'),

-- ULTRA TIER (96h, max stealth)
('ultra_96h', 'Ultra 96h', 'plays', 3.99, 96, 1000, 500000, '#ff0080', 'ULTRA', 'WORLDWIDE', 'Maximum stealth - 96h delivery'),
('ultra_geo', 'Ultra Geo-Mix', 'plays', 4.99, 96, 500, 250000, '#ff0080', 'ULTRA', 'MIX', 'Multi-region premium mix'),

-- MONTHLY LISTENERS
('listeners_ww', 'Monthly Listeners WW', 'listeners', 3.50, 72, 500, 500000, '#00ff88', 'STEALTH', 'WORLDWIDE', 'Monthly listener boost'),
('listeners_usa', 'Monthly Listeners USA', 'listeners', 5.50, 72, 500, 250000, '#ff0080', 'ULTRA', 'USA', 'USA monthly listeners'),

-- FOLLOWERS
('followers_artist', 'Artist Followers', 'followers', 4.00, 48, 100, 100000, '#00d4ff', 'STEALTH', 'WORLDWIDE', 'Artist profile followers'),
('followers_playlist', 'Playlist Followers', 'followers', 3.50, 48, 100, 100000, '#00d4ff', 'STEALTH', 'WORLDWIDE', 'Playlist followers'),

-- SAVES
('saves_track', 'Track Saves', 'saves', 5.00, 72, 100, 50000, '#ff0080', 'ULTRA', 'WORLDWIDE', 'Library saves'),

-- ENGAGEMENT
('engagement_mix', 'Engagement Mix', 'engagement', 6.99, 96, 100, 25000, '#ff0080', 'ULTRA', 'WORLDWIDE', 'Saves + Follows + Playlist Adds')
ON CONFLICT (service_id) DO UPDATE SET
    name = EXCLUDED.name,
    price_per_1000 = EXCLUDED.price_per_1000,
    delivery_hours = EXCLUDED.delivery_hours,
    neon_color = EXCLUDED.neon_color;

-- ==================== SEED: DEMO API KEY ====================
INSERT INTO api_keys (api_key, user_email, user_name, balance, total_spent) VALUES
('demo', 'demo@goodfellaz17.com', 'Demo Account', 2500.00, 150.00),
('test_abc123', 'test@goodfellaz17.com', 'Test User', 500.00, 50.00)
ON CONFLICT (api_key) DO UPDATE SET
    balance = EXCLUDED.balance;

-- ==================== VERIFY ====================
SELECT 'Services loaded:' as info, COUNT(*) as count FROM services;
SELECT 'API Keys loaded:' as info, COUNT(*) as count FROM api_keys;
