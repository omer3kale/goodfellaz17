-- =============================================================================
-- Flyway Migration: V11__Create_App_User.sql
-- 
-- PURPOSE: Create non-privileged application user for runtime operations
-- 
-- SECURITY MODEL:
--   spotifybot_admin  → DDL, migrations, backups, maintenance (SUPERUSER-like)
--   spotifybot_app    → DML only: SELECT, INSERT, UPDATE, DELETE (App runtime)
-- 
-- This mirrors Neon's "no superuser for application code" security model.
-- The application should NEVER connect as spotifybot_admin.
-- 
-- IDEMPOTENT: Safe to run multiple times.
-- 
-- MANUAL PASSWORD CHANGE:
--   After running this migration, change the app user password:
--   
--   docker exec -it goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
--     -c "ALTER ROLE spotifybot_app WITH PASSWORD 'YourStrongPasswordHere';"
--   
--   Or use: ./infra/setup-app-user.sh 'YourStrongPasswordHere'
-- 
-- @author Goodfellaz17 Security Hardening
-- @date 2026-01-19
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Step 1: Create role if not exists
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- Check if role exists
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'spotifybot_app') THEN
        -- Create with a placeholder password (MUST be changed after migration)
        CREATE ROLE spotifybot_app WITH 
            LOGIN 
            PASSWORD 'CHANGE_ME_IMMEDIATELY'
            NOSUPERUSER 
            NOCREATEDB 
            NOCREATEROLE 
            NOINHERIT
            CONNECTION LIMIT 50;  -- Prevent connection exhaustion attacks
        
        RAISE NOTICE 'Created role: spotifybot_app';
        RAISE NOTICE '⚠️  IMPORTANT: Change the password immediately!';
        RAISE NOTICE '   Run: ALTER ROLE spotifybot_app WITH PASSWORD ''YourStrongPassword'';';
    ELSE
        RAISE NOTICE 'Role spotifybot_app already exists, skipping creation';
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- Step 2: Revoke all default privileges (defense in depth)
-- -----------------------------------------------------------------------------
-- Revoke any PUBLIC grants on the database
REVOKE ALL ON DATABASE spotifybot FROM PUBLIC;

-- Revoke any existing privileges from spotifybot_app (clean slate)
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM spotifybot_app;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM spotifybot_app;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM spotifybot_app;
REVOKE ALL ON SCHEMA public FROM spotifybot_app;

-- -----------------------------------------------------------------------------
-- Step 3: Grant minimal required privileges
-- -----------------------------------------------------------------------------

-- Allow connecting to the database
GRANT CONNECT ON DATABASE spotifybot TO spotifybot_app;

-- Allow usage of the public schema (required to see tables)
GRANT USAGE ON SCHEMA public TO spotifybot_app;

-- Grant DML privileges on application tables (NO DDL!)
-- These are the 13 domain tables from the schema
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.users TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.orders TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.order_tasks TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.proxy_nodes TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.proxy_metrics TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.refund_events TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.refund_anomalies TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.services TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.tor_circuits TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.device_nodes TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.premium_accounts TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.invariant_check_log TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.balance_transactions TO spotifybot_app;

-- Grant read-only access to views (for monitoring queries)
GRANT SELECT ON TABLE public.v_invariant_health TO spotifybot_app;
GRANT SELECT ON TABLE public.v_proxy_selection TO spotifybot_app;

-- Grant sequence usage (required for INSERT with auto-generated IDs)
-- Note: Our tables use UUID with uuid_generate_v4(), but grant anyway for safety
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO spotifybot_app;

-- -----------------------------------------------------------------------------
-- Step 4: Set default privileges for future tables
-- -----------------------------------------------------------------------------
-- If spotifybot_admin creates new tables, spotifybot_app gets DML access
ALTER DEFAULT PRIVILEGES FOR ROLE spotifybot_admin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO spotifybot_app;

ALTER DEFAULT PRIVILEGES FOR ROLE spotifybot_admin IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO spotifybot_app;

-- -----------------------------------------------------------------------------
-- Step 5: Explicitly DENY dangerous privileges
-- -----------------------------------------------------------------------------
-- spotifybot_app cannot:
--   - CREATE tables, indexes, or schemas
--   - ALTER existing objects
--   - DROP anything
--   - TRUNCATE tables (data destruction without logging)
--   - Execute arbitrary functions (unless explicitly granted)

-- Revoke TRUNCATE explicitly (it's sometimes included in DELETE)
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM spotifybot_app;

-- Revoke ability to create anything in public schema
REVOKE CREATE ON SCHEMA public FROM spotifybot_app;

-- -----------------------------------------------------------------------------
-- Step 6: Verification query (for manual review)
-- -----------------------------------------------------------------------------
-- Run this to verify grants:
-- SELECT grantee, table_name, privilege_type 
-- FROM information_schema.table_privileges 
-- WHERE grantee = 'spotifybot_app' 
-- ORDER BY table_name, privilege_type;

-- -----------------------------------------------------------------------------
-- Comments for documentation
-- -----------------------------------------------------------------------------
COMMENT ON ROLE spotifybot_app IS 
    'Non-privileged application user for Goodfellaz17 runtime. '
    'DML only (SELECT, INSERT, UPDATE, DELETE). '
    'No DDL privileges. '
    'spotifybot_admin should be used for migrations, backups, and maintenance.';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'V11__Create_App_User.sql completed successfully';
    RAISE NOTICE '============================================================';
    RAISE NOTICE '';
    RAISE NOTICE 'spotifybot_app has been granted:';
    RAISE NOTICE '  ✓ SELECT, INSERT, UPDATE, DELETE on all 13 domain tables';
    RAISE NOTICE '  ✓ SELECT on views (v_invariant_health, v_proxy_selection)';
    RAISE NOTICE '  ✓ USAGE, SELECT on all sequences';
    RAISE NOTICE '';
    RAISE NOTICE 'spotifybot_app is DENIED:';
    RAISE NOTICE '  ✗ CREATE, ALTER, DROP (DDL)';
    RAISE NOTICE '  ✗ TRUNCATE';
    RAISE NOTICE '  ✗ Superuser privileges';
    RAISE NOTICE '';
    RAISE NOTICE '⚠️  NEXT STEPS:';
    RAISE NOTICE '  1. Change password: ./infra/setup-app-user.sh ''YourPassword''';
    RAISE NOTICE '  2. Update application-local-selfhosted.yml';
    RAISE NOTICE '  3. Restart the application';
    RAISE NOTICE '';
END
$$;
