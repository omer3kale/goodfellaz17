-- GOODFELLAZ17 Production Database Schema
-- ========================================
-- Run in Neon PostgreSQL Console
-- https://console.neon.tech

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===========================================
-- PREMIUM ACCOUNTS TABLE (Spotify farm accounts)
-- ===========================================
CREATE TABLE IF NOT EXISTS premium_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(100) NOT NULL,
    password VARCHAR(100),
    cookies TEXT,
    spotify_refresh_token TEXT,
    spotify_access_token TEXT,
    region VARCHAR(20) DEFAULT 'WORLDWIDE',
    premium_expiry DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ===========================================
-- ORDERS TABLE
-- ===========================================
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_id VARCHAR(50) NOT NULL,
    service_name VARCHAR(100),
    track_url TEXT,
    quantity INT NOT NULL,
    delivered INT DEFAULT 0,
    geo_target VARCHAR(20) DEFAULT 'WORLDWIDE',
    speed_tier VARCHAR(20) DEFAULT 'NORMAL',
    status VARCHAR(20) DEFAULT 'PENDING',
    rate_per_thousand DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

-- ===========================================
-- USER PROXIES TABLE (botzzz773.pro users)
-- ===========================================
CREATE TABLE IF NOT EXISTS user_proxies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent TEXT,
    geo_target VARCHAR(20) DEFAULT 'WORLDWIDE',
    has_spotify_premium BOOLEAN DEFAULT false,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    total_earnings DECIMAL(10,2) DEFAULT 0,
    total_tasks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    last_seen TIMESTAMP
);

-- ===========================================
-- TASK ASSIGNMENTS TABLE
-- ===========================================
CREATE TABLE IF NOT EXISTS task_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id),
    user_proxy_id UUID REFERENCES user_proxies(id),
    track_url TEXT,
    commission DECIMAL(10,4),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

-- ===========================================
-- REVENUE TRACKING
-- ===========================================
CREATE TABLE IF NOT EXISTS revenue_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id),
    amount DECIMAL(10,2),
    source VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ===========================================
-- INDEXES FOR PERFORMANCE
-- ===========================================
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_premium_accounts_expiry ON premium_accounts(premium_expiry);
CREATE INDEX IF NOT EXISTS idx_user_proxies_status ON user_proxies(status);

-- ===========================================
-- SAMPLE PRODUCTION DATA 
-- REPLACE WITH YOUR REAL OAUTH TOKENS FROM /api/auth/spotify/login
-- ===========================================

-- Step 1: Go to https://goodfellaz17.onrender.com/api/auth/spotify/login
-- Step 2: Login with your Spotify Premium farm account
-- Step 3: Copy the refresh_token from the response
-- Step 4: Run this INSERT with your real token:

-- INSERT INTO premium_accounts (email, spotify_refresh_token, region, premium_expiry) 
-- VALUES ('your-farm-account@email.com', 'YOUR_REAL_OAUTH_REFRESH_TOKEN', 'USA', '2026-06-25');

-- ===========================================
-- VERIFY TABLES
-- ===========================================
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
