# Multi-Tenant Guide

> **Adding and Managing Tenant Databases in Goodfellaz17**  
> How to support multiple SMM panels (botzzz773, etc.) on shared infrastructure.

---

## Table of Contents

1. [Overview](#overview)
2. [Adding a New Tenant](#adding-a-new-tenant)
3. [Tenant Routing](#tenant-routing)
4. [Security & Isolation](#security--isolation)
5. [Schema Versioning](#schema-versioning)
6. [Quota Management](#quota-management)
7. [Monitoring Tenants](#monitoring-tenants)
8. [Removing a Tenant](#removing-a-tenant)

---

## Overview

### What is Multi-Tenancy?

Multi-tenancy allows multiple isolated "tenants" (SMM panels) to share the same infrastructure while keeping their data completely separate.

```
┌───────────────────────────────────────────────────────────────┐
│                    SHARED INFRASTRUCTURE                       │
│                                                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ goodfellaz17 │  │  botzzz773   │  │   future_x   │        │
│  │  (primary)   │  │  (tenant 2)  │  │  (tenant N)  │        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
│         │                 │                  │                 │
│         ▼                 ▼                  ▼                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │   spotifybot │  │ botzzz773_db │  │ future_x_db  │        │
│  │  (database)  │  │  (database)  │  │  (database)  │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                │
│              All managed by tenant_databases catalog           │
└───────────────────────────────────────────────────────────────┘
```

### Benefits

| Benefit | Description |
|---------|-------------|
| **Data Isolation** | Tenant A cannot access Tenant B's data |
| **Independent Backups** | Backup/restore individual tenants |
| **Custom Quotas** | Different limits per tenant |
| **Schema Flexibility** | Tenants can be at different schema versions (temporarily) |
| **Billing Ready** | Track usage per tenant |

---

## Adding a New Tenant

### Method 1: Using branch-db.sh (Recommended)

```bash
# Create tenant with empty database
./infra/branch-db.sh botzzz773

# Create tenant with sanitized data from production
./infra/branch-db.sh botzzz773 --with-data
```

This script automatically:
1. Creates database `spotifybot_botzzz773`
2. Runs all migrations (V1-V14)
3. Grants `spotifybot_app` access
4. Creates Spring profile `application-botzzz773.yml`
5. Registers in `tenant_databases` catalog

### Method 2: Manual Setup

#### Step 1: Create Database

```bash
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d postgres << 'EOF'
CREATE DATABASE spotifybot_botzzz773 
    WITH OWNER = spotifybot_admin 
    ENCODING = 'UTF8';
EOF
```

#### Step 2: Run Migrations

```bash
for sql in src/main/resources/db/migration/V*.sql; do
    echo "Running: $(basename $sql)"
    docker exec -i goodfellaz17-postgres psql \
        -U spotifybot_admin -d spotifybot_botzzz773 < "$sql"
done
```

#### Step 3: Grant App User Access

```bash
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot_botzzz773 << 'EOF'
GRANT USAGE ON SCHEMA public TO spotifybot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO spotifybot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO spotifybot_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO spotifybot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT USAGE, SELECT ON SEQUENCES TO spotifybot_app;
EOF
```

#### Step 4: Register in Catalog

```bash
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot << 'EOF'
INSERT INTO tenant_databases (
    tenant_code,
    database_name,
    display_name,
    owner_email,
    host,
    port,
    current_schema_version,
    target_schema_version,
    backup_enabled,
    backup_schedule,
    retention_days,
    quota_storage_gb,
    quota_orders_per_day,
    status
) VALUES (
    'botzzz773',
    'spotifybot_botzzz773',
    'Botzzz773 SMM Panel',
    'admin@botzzz773.com',
    'localhost',
    5432,
    'V14',
    'V14',
    true,
    '0 4 * * *',
    30,
    20,           -- 20 GB storage quota
    50000,        -- 50k orders per day
    'ACTIVE'
);
EOF
```

#### Step 5: Create API Key

```bash
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot << 'EOF'
INSERT INTO tenant_api_keys (
    tenant_id,
    key_name,
    api_key,
    key_prefix,
    permissions,
    rate_limit_per_minute
) VALUES (
    (SELECT id FROM tenant_databases WHERE tenant_code = 'botzzz773'),
    'production',
    'bz773_prod_' || encode(gen_random_bytes(24), 'hex'),
    'bz773_',
    '["read", "write"]',
    2000
);

-- Display the generated key
SELECT api_key FROM tenant_api_keys 
WHERE tenant_id = (SELECT id FROM tenant_databases WHERE tenant_code = 'botzzz773');
EOF
```

#### Step 6: Create Spring Profile

Create `src/main/resources/application-botzzz773.yml`:

```yaml
spring:
  config:
    import: classpath:application-local-selfhosted.yml
    
  r2dbc:
    url: r2dbc:postgresql://localhost:6432/spotifybot_botzzz773
    username: spotifybot_app
    password: ${SPOTIFYBOT_APP_PASSWORD:SpotifyApp2026Secure}

goodfellaz17:
  multitenancy:
    enabled: false
    default-tenant: botzzz773
```

---

## Tenant Routing

### How Routing Works

```
┌─────────────────────────────────────────────────────────────┐
│                      INCOMING REQUEST                        │
│                                                              │
│   Headers:                                                   │
│   - X-Tenant-ID: botzzz773                                  │
│   - Authorization: Bearer bz773_prod_abc123...              │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    TENANT FILTER                             │
│                                                              │
│   1. Extract X-Tenant-ID header OR                          │
│   2. Resolve tenant from API key                            │
│   3. Set TenantContext.setTenant(tenantInfo)               │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  DATASOURCE ROUTER                           │
│                                                              │
│   1. TenantContext.require() → "botzzz773"                  │
│   2. Look up connection pool for botzzz773                  │
│   3. Return DatabaseClient for spotifybot_botzzz773        │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                 REPOSITORY OPERATION                         │
│                                                              │
│   SELECT * FROM orders WHERE ...                            │
│   → Executes against spotifybot_botzzz773 database         │
└─────────────────────────────────────────────────────────────┘
```

### Configuration

```yaml
# application-local-selfhosted.yml
goodfellaz17:
  multitenancy:
    enabled: true
    default-tenant: goodfellaz17
    
    resolution:
      header-name: X-Tenant-ID
      api-key-enabled: true
      strict-mode: true  # Fail if tenant cannot be resolved
```

### API Usage

```bash
# Using header
curl -H "X-Tenant-ID: botzzz773" http://localhost:8080/api/v2/orders

# Using API key (resolves tenant automatically)
curl -H "Authorization: Bearer bz773_prod_abc123..." http://localhost:8080/api/v2/orders
```

---

## Security & Isolation

### Database-Level Isolation

Each tenant has its own database. There is no shared tables, no row-level security needed.

```
PostgreSQL Instance
├── spotifybot          (goodfellaz17's data)
├── spotifybot_botzzz773  (botzzz773's data)
└── spotifybot_future     (future tenant)
```

### Application-Level Protection

```java
// DatasourceRouter.java
public Mono<Void> validateOrderBelongsToTenant(String orderId) {
    String tenantCode = TenantContext.getTenantCodeOrUnknown();
    
    return getDatabaseClient()
        .sql("SELECT 1 FROM orders WHERE id = :orderId")
        .bind("orderId", orderId)
        .fetch()
        .first()
        .switchIfEmpty(Mono.error(new CrossTenantAccessException(
            "Order " + orderId + " not found in tenant " + tenantCode
        )))
        .then();
}
```

### Fail-Safe Defaults

| Scenario | Behavior |
|----------|----------|
| No tenant header | Throws `TenantContextMissingException` |
| Unknown tenant code | Throws `UnknownTenantException` |
| Cross-tenant access | Throws `CrossTenantAccessException` |
| Missing API key | Uses `default-tenant` (if configured) |

---

## Schema Versioning

### The Problem

```
Day 1: Deploy V14 to goodfellaz17
Day 2: botzzz773 is still at V12

App expects V14 features → botzzz773 fails!
```

### The Solution

```sql
-- tenant_databases tracks schema versions
SELECT tenant_code, current_schema_version, target_schema_version
FROM tenant_databases;

-- Output:
-- goodfellaz17 | V14 | V14    ← Synced
-- botzzz773    | V12 | V14    ← NEEDS MIGRATION!
```

### Startup Check

```java
// Application startup validates schema version
@PostConstruct
public void validateSchemaVersions() {
    tenantDatabases.findAll()
        .filter(t -> !t.currentVersion().equals(t.targetVersion()))
        .collectList()
        .doOnNext(outdated -> {
            if (!outdated.isEmpty()) {
                throw new SchemaVersionMismatchException(
                    "Tenants need migration: " + outdated
                );
            }
        })
        .subscribe();
}
```

### Migration Tool

```bash
# Check which tenants need migrations
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
    -c "SELECT * FROM v_tenant_status;"

# Run migrations for specific tenant
./infra/migrate-tenant.sh botzzz773

# Run migrations for all tenants
./infra/migrate-all-tenants.sh
```

---

## Quota Management

### Available Quotas

| Quota | Default | Description |
|-------|---------|-------------|
| `quota_storage_gb` | 10 | Maximum database size |
| `quota_orders_per_day` | 10000 | Daily order limit |
| `quota_connections_max` | 20 | Maximum concurrent connections |

### Setting Quotas

```sql
-- Update quotas for a tenant
UPDATE tenant_databases
SET quota_storage_gb = 50,
    quota_orders_per_day = 100000,
    quota_connections_max = 30
WHERE tenant_code = 'botzzz773';
```

### Quota Enforcement

```sql
-- Check if tenant can create more orders
SELECT check_tenant_order_quota('botzzz773');  -- Returns TRUE/FALSE
```

### Usage Tracking

```sql
-- View current usage
SELECT 
    tenant_code,
    database_size_mb,
    storage_quota_used_pct,
    orders_created as orders_today,
    orders_quota_used_pct
FROM tenant_usage_metrics tum
JOIN tenant_databases td ON tum.tenant_id = td.id
WHERE metric_date = CURRENT_DATE;
```

---

## Monitoring Tenants

### Dashboard View

```sql
-- Overall tenant status
SELECT * FROM v_tenant_status;
```

Output:
```
tenant_code   | status | schema_status    | storage_used_pct | last_backup_at
--------------+--------+------------------+------------------+------------------
goodfellaz17  | ACTIVE | SYNCED           | 12.5%            | 2026-01-19 03:00
botzzz773     | ACTIVE | NEEDS_MIGRATION  | 8.2%             | 2026-01-19 04:00
```

### Connection Pool Stats

```java
// In application code
@GetMapping("/internal/tenants/pools")
public Map<String, PoolStats> getPoolStats() {
    return datasourceRouter.getPoolStatistics();
}
```

### Health Check per Tenant

```bash
# Check specific tenant database
curl http://localhost:8080/actuator/health/db?tenant=botzzz773
```

---

## Removing a Tenant

### Step 1: Backup First

```bash
docker exec goodfellaz17-postgres pg_dump -U spotifybot_admin \
    -d spotifybot_botzzz773 | gzip > botzzz773_final_backup.sql.gz
```

### Step 2: Mark as Archived

```sql
UPDATE tenant_databases 
SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
WHERE tenant_code = 'botzzz773';
```

### Step 3: Remove from Router

```java
// In application
datasourceRouter.unregisterTenant("botzzz773");
```

### Step 4: Drop Database (Optional)

```bash
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d postgres \
    -c "DROP DATABASE spotifybot_botzzz773;"
```

### Step 5: Clean Up Files

```bash
rm src/main/resources/application-botzzz773.yml
```

---

## Appendix: Tenant Database Schema

```sql
CREATE TABLE tenant_databases (
    id                      UUID PRIMARY KEY,
    tenant_code             VARCHAR(50) UNIQUE,     -- 'goodfellaz17'
    database_name           VARCHAR(100) UNIQUE,    -- 'spotifybot'
    display_name            VARCHAR(255),           -- 'Goodfellaz17 Production'
    owner_email             VARCHAR(255),
    
    -- Connection
    host                    VARCHAR(255) DEFAULT 'localhost',
    port                    INTEGER DEFAULT 5432,
    connection_pool_size    INTEGER DEFAULT 10,
    
    -- Schema
    current_schema_version  VARCHAR(20) DEFAULT 'V1',
    target_schema_version   VARCHAR(20) DEFAULT 'V14',
    
    -- Backup
    backup_enabled          BOOLEAN DEFAULT TRUE,
    backup_schedule         VARCHAR(50) DEFAULT '0 3 * * *',
    retention_days          INTEGER DEFAULT 30,
    
    -- Quotas
    quota_storage_gb        INTEGER DEFAULT 10,
    quota_orders_per_day    INTEGER DEFAULT 10000,
    quota_connections_max   INTEGER DEFAULT 20,
    
    -- Status
    status                  VARCHAR(50) DEFAULT 'ACTIVE'
);
```
