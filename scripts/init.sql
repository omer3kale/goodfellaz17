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

-- ==================== SERVICES (UUID PK for generated entities) ====================
CREATE TABLE IF NOT EXISTS services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    description VARCHAR(1000),
    cost_per_1k DECIMAL(8,2) NOT NULL,
    reseller_cost_per_1k DECIMAL(8,2) NOT NULL,
    agency_cost_per_1k DECIMAL(8,2) NOT NULL,
    min_quantity INTEGER NOT NULL DEFAULT 100,
    max_quantity INTEGER NOT NULL DEFAULT 1000000,
    estimated_days_min INTEGER NOT NULL DEFAULT 1,
    estimated_days_max INTEGER NOT NULL DEFAULT 7,
    geo_profiles JSONB NOT NULL DEFAULT '["WORLDWIDE"]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT services_type_check CHECK (service_type IN (
        'PLAYS', 'MONTHLY_LISTENERS', 'SAVES', 'FOLLOWS', 
        'PLAYLIST_FOLLOWERS', 'PLAYLIST_PLAYS'
    )),
    CONSTRAINT services_cost_positive CHECK (cost_per_1k > 0)
);

CREATE INDEX IF NOT EXISTS idx_services_type_active ON services(service_type, is_active);
CREATE INDEX IF NOT EXISTS idx_services_sort ON services(sort_order);

-- ==================== ORDERS (Generated entity schema - complete) ====================
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_id UUID NOT NULL,
    service_name VARCHAR(255),
    price_per_unit DECIMAL(12,4),
    total_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    quantity INTEGER NOT NULL,
    delivered INTEGER NOT NULL DEFAULT 0,
    remains INTEGER NOT NULL DEFAULT 0,
    target_url VARCHAR(512) NOT NULL,
    geo_profile VARCHAR(50) NOT NULL DEFAULT 'WORLDWIDE',
    speed_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    start_count INTEGER,
    current_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    internal_notes VARCHAR(1000),
    external_order_id VARCHAR(64),
    webhook_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_completion_at TIMESTAMP WITH TIME ZONE,
    failed_permanent_plays INTEGER NOT NULL DEFAULT 0,
    uses_task_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT orders_delivered_valid CHECK (delivered >= 0 AND delivered <= quantity),
    CONSTRAINT orders_status_check CHECK (status IN (
        'PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED', 
        'PARTIAL', 'FAILED', 'REFUNDED', 'CANCELLED'
    )),
    CONSTRAINT orders_speed_valid CHECK (speed_multiplier >= 0.1 AND speed_multiplier <= 5.0)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders(user_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders(status, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_target_url ON orders(target_url);
CREATE INDEX IF NOT EXISTS idx_orders_external_id ON orders(external_order_id) WHERE external_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_pending_running ON orders(status) WHERE status IN ('PENDING', 'RUNNING');

-- ==================== BALANCE TRANSACTIONS (Generated entity schema) ====================
CREATE TABLE IF NOT EXISTS balance_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    amount DECIMAL(10,2) NOT NULL,
    balance_before DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    payment_provider VARCHAR(50),
    external_tx_id VARCHAR(128),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT balance_tx_type_check CHECK (type IN (
        'DEBIT', 'CREDIT', 'REFUND', 'BONUS', 'ADJUSTMENT'
    ))
);

CREATE INDEX IF NOT EXISTS idx_balance_tx_user_time ON balance_transactions(user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_balance_tx_type ON balance_transactions(type);
CREATE INDEX IF NOT EXISTS idx_balance_tx_order ON balance_transactions(order_id) WHERE order_id IS NOT NULL;

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

-- ==================== PROXY NODES (Capacity tracking) ====================
CREATE TABLE IF NOT EXISTS proxy_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    provider_instance_id VARCHAR(64),
    public_ip VARCHAR(45) NOT NULL,
    port INTEGER NOT NULL,
    region VARCHAR(50) NOT NULL,
    country VARCHAR(2) NOT NULL,
    city VARCHAR(100),
    tier VARCHAR(50) NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 100,
    current_load INTEGER NOT NULL DEFAULT 0,
    cost_per_hour DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
    auth_username VARCHAR(64),
    auth_password VARCHAR(128),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_healthcheck TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    tags JSONB,
    
    CONSTRAINT proxy_nodes_ip_unique UNIQUE (public_ip),
    CONSTRAINT proxy_nodes_port_valid CHECK (port >= 1 AND port <= 65535),
    CONSTRAINT proxy_nodes_capacity_positive CHECK (capacity > 0),
    CONSTRAINT proxy_nodes_load_valid CHECK (current_load >= 0)
);

CREATE INDEX IF NOT EXISTS idx_proxy_nodes_tier_status ON proxy_nodes(tier, status);
CREATE INDEX IF NOT EXISTS idx_proxy_nodes_region_tier ON proxy_nodes(region, tier);
CREATE INDEX IF NOT EXISTS idx_proxy_nodes_provider ON proxy_nodes(provider);
CREATE INDEX IF NOT EXISTS idx_proxy_nodes_country_tier ON proxy_nodes(country, tier);

-- Seed initial proxy nodes for testing (need >208 plays/hr for 15k in 72h)
-- DATACENTER: 1.0x → ~14/hr each, RESIDENTIAL: 2.0x → ~28/hr, MOBILE: 2.5x → ~35/hr
INSERT INTO proxy_nodes (provider, public_ip, port, region, country, tier, capacity, status) VALUES
-- 10x DATACENTER (~140 plays/hr)
('VULTR', '10.0.0.1', 3128, 'us-west', 'US', 'DATACENTER', 500, 'ONLINE'),
('VULTR', '10.0.0.2', 3128, 'us-west', 'US', 'DATACENTER', 500, 'ONLINE'),
('VULTR', '10.0.0.3', 3128, 'us-west', 'US', 'DATACENTER', 500, 'ONLINE'),
('HETZNER', '10.0.0.4', 3128, 'eu-central', 'DE', 'DATACENTER', 500, 'ONLINE'),
('HETZNER', '10.0.0.5', 3128, 'eu-central', 'DE', 'DATACENTER', 500, 'ONLINE'),
('HETZNER', '10.0.0.6', 3128, 'eu-central', 'DE', 'DATACENTER', 500, 'ONLINE'),
('OVH', '10.0.0.7', 3128, 'eu-west', 'FR', 'DATACENTER', 500, 'ONLINE'),
('OVH', '10.0.0.8', 3128, 'eu-west', 'FR', 'DATACENTER', 500, 'ONLINE'),
('LINODE', '10.0.0.9', 3128, 'ap-south', 'JP', 'DATACENTER', 500, 'ONLINE'),
('LINODE', '10.0.0.10', 3128, 'ap-south', 'JP', 'DATACENTER', 500, 'ONLINE'),
-- 5x RESIDENTIAL (~140 plays/hr)
('VULTR', '10.0.1.1', 3128, 'us-east', 'US', 'RESIDENTIAL', 200, 'ONLINE'),
('VULTR', '10.0.1.2', 3128, 'us-east', 'US', 'RESIDENTIAL', 200, 'ONLINE'),
('HETZNER', '10.0.1.3', 3128, 'eu-central', 'DE', 'RESIDENTIAL', 200, 'ONLINE'),
('HETZNER', '10.0.1.4', 3128, 'eu-west', 'FR', 'RESIDENTIAL', 200, 'ONLINE'),
('OVH', '10.0.1.5', 3128, 'eu-west', 'GB', 'RESIDENTIAL', 200, 'ONLINE'),
-- 3x MOBILE (~105 plays/hr)
('VULTR', '10.0.2.1', 3128, 'us-west', 'US', 'MOBILE', 100, 'ONLINE'),
('HETZNER', '10.0.2.2', 3128, 'eu-central', 'DE', 'MOBILE', 100, 'ONLINE'),
('LINODE', '10.0.2.3', 3128, 'ap-south', 'JP', 'MOBILE', 100, 'ONLINE')
ON CONFLICT (public_ip) DO NOTHING;

-- ==================== PROXY METRICS (Performance tracking) ====================
CREATE TABLE IF NOT EXISTS proxy_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proxy_node_id UUID NOT NULL REFERENCES proxy_nodes(id) ON DELETE CASCADE,
    total_requests BIGINT NOT NULL DEFAULT 0,
    successful_requests BIGINT NOT NULL DEFAULT 0,
    failed_requests BIGINT NOT NULL DEFAULT 0,
    success_rate DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ban_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    latency_p50 INTEGER NOT NULL DEFAULT 0,
    latency_p95 INTEGER NOT NULL DEFAULT 0,
    latency_p99 INTEGER NOT NULL DEFAULT 0,
    active_connections INTEGER NOT NULL DEFAULT 0,
    peak_connections INTEGER NOT NULL DEFAULT 0,
    error_codes JSONB,
    bytes_transferred BIGINT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    window_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT proxy_metrics_node_unique UNIQUE (proxy_node_id)
);

CREATE INDEX IF NOT EXISTS idx_proxy_metrics_node ON proxy_metrics(proxy_node_id);
CREATE INDEX IF NOT EXISTS idx_proxy_metrics_success_rate ON proxy_metrics(success_rate DESC);
CREATE INDEX IF NOT EXISTS idx_proxy_metrics_last_updated ON proxy_metrics(last_updated);

-- Seed proxy metrics for each proxy node
INSERT INTO proxy_metrics (proxy_node_id, total_requests, successful_requests, success_rate)
SELECT id, 1000, 980, 0.98 FROM proxy_nodes
ON CONFLICT (proxy_node_id) DO NOTHING;

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
CREATE INDEX IF NOT EXISTS idx_bot_tasks_order_id ON bot_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_bot_tasks_status ON bot_tasks(status);
CREATE INDEX IF NOT EXISTS idx_api_keys_api_key ON api_keys(api_key);
CREATE INDEX IF NOT EXISTS idx_transactions_api_key ON transactions(api_key);

-- ==================== SEED: TEST USER with API key for freeze tests ====================
INSERT INTO users (id, email, password_hash, tier, balance, api_key, status) VALUES
('00000000-0000-0000-0000-000000000001', 'test@goodfellaz17.com', '$2a$10$N9qo8uLOickgx2ZMRZoMy.Mr8z0nVF7rMBvKAP0M7Fz3F', 'AGENCY', 10000.00, 'test-api-key-local-dev-12345', 'ACTIVE'),
('00000000-0000-0000-0000-000000000002', 'demo@goodfellaz17.com', '$2a$10$N9qo8uLOickgx2ZMRZoMy.Mr8z0nVF7rMBvKAP0M7Fz3F', 'CONSUMER', 2500.00, 'demo', 'ACTIVE')
ON CONFLICT (email) DO UPDATE SET 
    balance = EXCLUDED.balance, 
    api_key = EXCLUDED.api_key,
    status = EXCLUDED.status;

-- ==================== SEED: SERVICES (UUID format for freeze tests) ====================
-- Service used by freeze test: 3c1cb593-85a7-4375-8092-d39c00399a7b
INSERT INTO services (id, name, display_name, service_type, cost_per_1k, reseller_cost_per_1k, agency_cost_per_1k, min_quantity, max_quantity, estimated_days_min, estimated_days_max, description, is_active, sort_order) VALUES
-- FREEZE TEST SERVICE (required for 15k-freeze-test.sh)
('3c1cb593-85a7-4375-8092-d39c00399a7b', 'freeze_test_plays', 'Freeze Test Plays', 'PLAYS', 2.00, 1.50, 1.00, 100, 1000000, 1, 3, 'Test service for freeze tests', true, 0),
-- Regular services
('a1111111-1111-1111-1111-111111111111', 'lightning_12h', 'Lightning 12h', 'PLAYS', 1.99, 1.49, 0.99, 1000, 1000000, 1, 1, 'Fastest delivery - 12 hour completion', true, 1),
('a2222222-2222-2222-2222-222222222222', 'stealth_48h', 'Stealth 48h', 'PLAYS', 2.49, 1.99, 1.49, 1000, 1000000, 2, 2, 'Drip-scheduled organic pattern', true, 2),
('a3333333-3333-3333-3333-333333333333', 'ultra_96h', 'Ultra 96h', 'PLAYS', 3.99, 2.99, 1.99, 1000, 500000, 3, 4, 'Maximum stealth - 96h delivery', true, 3),
('a4444444-4444-4444-4444-444444444444', 'listeners_ww', 'Monthly Listeners WW', 'MONTHLY_LISTENERS', 3.50, 2.50, 1.50, 500, 500000, 3, 7, 'Monthly listener boost', true, 4),
('a5555555-5555-5555-5555-555555555555', 'followers_artist', 'Artist Followers', 'FOLLOWS', 4.00, 3.00, 2.00, 100, 100000, 2, 5, 'Artist profile followers', true, 5),
('a6666666-6666-6666-6666-666666666666', 'saves_track', 'Track Saves', 'SAVES', 5.00, 3.50, 2.50, 100, 50000, 3, 7, 'Library saves', true, 6)
ON CONFLICT (name) DO UPDATE SET
    cost_per_1k = EXCLUDED.cost_per_1k,
    reseller_cost_per_1k = EXCLUDED.reseller_cost_per_1k,
    agency_cost_per_1k = EXCLUDED.agency_cost_per_1k;

-- ==================== SEED: DEMO API KEY ====================
INSERT INTO api_keys (api_key, user_email, user_name, balance, total_spent) VALUES
('demo', 'demo@goodfellaz17.com', 'Demo Account', 2500.00, 150.00),
('test_abc123', 'test@goodfellaz17.com', 'Test User', 500.00, 50.00)
ON CONFLICT (api_key) DO UPDATE SET
    balance = EXCLUDED.balance;

-- ==================== VERIFY ====================
SELECT 'Users loaded:' as info, COUNT(*) as count FROM users;
SELECT 'Services loaded:' as info, COUNT(*) as count FROM services;
SELECT 'Proxy nodes loaded:' as info, COUNT(*) as count FROM proxy_nodes;
