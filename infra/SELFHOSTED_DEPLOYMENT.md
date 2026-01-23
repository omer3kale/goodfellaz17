# Self-Hosted Deployment Guide

> **Goodfellaz17 PostgreSQL Platform**  
> A production-grade, self-hosted database infrastructure that's better than Neon/Supabase.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [PgBouncer Connection Pooling](#pgbouncer-connection-pooling)
5. [Database Migrations](#database-migrations)
6. [Security Configuration](#security-configuration)
7. [Backup & Recovery](#backup--recovery)
8. [Multi-Tenancy](#multi-tenancy)
9. [Monitoring & Health Checks](#monitoring--health-checks)
10. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GOODFELLAZ17 PLATFORM                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐   │
│   │  Spring App │────│  PgBouncer  │────│   PostgreSQL 16.9   │   │
│   │  (Port 8080)│    │  (Port 6432)│    │     (Port 5432)     │   │
│   └─────────────┘    └─────────────┘    └─────────────────────┘   │
│         │                  │                      │               │
│         │                  │                      │               │
│   ┌─────▼─────┐    ┌──────▼──────┐    ┌─────────▼─────────┐     │
│   │ R2DBC     │    │ Transaction │    │ Data Checksums    │     │
│   │ Reactive  │    │ Pooling     │    │ PITR Ready        │     │
│   └───────────┘    └─────────────┘    └───────────────────┘     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Why This Architecture?

| Component | Purpose | Why It's Better |
|-----------|---------|-----------------|
| **PostgreSQL 16.9** | Primary database | Data checksums, latest features |
| **PgBouncer** | Connection pooling | 1000 clients → 25 DB connections |
| **Two-User Model** | Security | Admin (DDL) vs App (DML only) |
| **Flyway** | Schema management | Versioned, auditable migrations |
| **Audit Triggers** | Compliance | Immutable change history |

---

## Prerequisites

### Software Requirements

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 24.0+ | Container runtime |
| Docker Compose | 2.20+ | Multi-container orchestration |
| Java | 17 (LTS) | Spring Boot runtime |
| Maven | 3.9+ | Build tool |

### Hardware (Minimum)

- **Mac (Development):** 8GB RAM, 20GB disk
- **HP Omen (Production):** 16GB RAM, 100GB SSD
- **VPS (Cloud):** 4 vCPU, 8GB RAM, 50GB SSD

---

## Quick Start

### 1. Start Infrastructure

```bash
# Navigate to project root
cd /path/to/goodfellaz17

# Start PostgreSQL + PgBouncer
docker compose -f infra/docker-compose.db.yml up -d

# Verify containers
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output:
```
NAMES                    STATUS                    PORTS
goodfellaz17-pgbouncer   Up 2 minutes (healthy)    0.0.0.0:6432->5432/tcp
goodfellaz17-postgres    Up 2 minutes (healthy)    0.0.0.0:5432->5432/tcp
```

### 2. Initialize Schema

```bash
# Run all migrations (V1-V14)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

for sql in src/main/resources/db/migration/V*.sql; do
    echo "Running: $(basename $sql)"
    docker exec -i goodfellaz17-postgres psql \
        -U spotifybot_admin -d spotifybot < "$sql"
done
```

### 3. Configure App User

```bash
# Set password for non-privileged app user
./infra/setup-app-user.sh 'SpotifyApp2026Secure'

# Verify (should see "DML allowed, DDL denied")
docker exec -it goodfellaz17-postgres psql -U spotifybot_app -d spotifybot \
    -c "SELECT count(*) FROM services;"
```

### 4. Start Application

```bash
# Using PgBouncer (recommended)
SPRING_PROFILES_ACTIVE=local-selfhosted mvn spring-boot:run

# Test health endpoint
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

---

## PgBouncer Connection Pooling

### Why PgBouncer?

Without pooling:
```
10 worker pods × 5 connections = 50 direct DB connections
Database max_connections = 100
→ At 20 pods, you hit connection exhaustion!
```

With PgBouncer:
```
10 worker pods × 5 connections = 50 client connections to PgBouncer
PgBouncer pool_size = 25 actual DB connections
→ Scale to 100+ pods without issues
```

### Transaction vs Session Pooling

| Mode | Connection Lifecycle | Use Case |
|------|---------------------|----------|
| **Transaction** (chosen) | Released after each transaction | Stateless APIs, high throughput |
| Session | Held for entire session | Requires LISTEN/NOTIFY, prepared stmts |

We chose **transaction mode** because:
- R2DBC is stateless (no session state needed)
- Each request = one transaction = one connection borrow
- Maximum connection utilization

### Configuration

```yaml
# PgBouncer settings (docker-compose.db.yml)
POOL_MODE: transaction
MAX_CLIENT_CONN: 1000        # Clients can connect to PgBouncer
DEFAULT_POOL_SIZE: 25        # Actual Postgres connections
RESERVE_POOL_SIZE: 5         # Burst capacity
```

### Connection Flow

```
Application (port 6432)
        ↓
    PgBouncer
    ├── Pool: goodfellaz17 (25 connections)
    ├── Pool: botzzz773 (10 connections)
    └── Pool: feature_proxy_test (5 connections)
        ↓
PostgreSQL (port 5432)
    └── max_connections = 100
```

---

## Database Migrations

### Migration Structure

```
src/main/resources/db/migration/
├── V1__Initial_Schema.sql         # Core tables
├── V2__Fix_Schema_Mismatches.sql  # Fixes
├── V3__Premium_Accounts.sql       # Bot accounts
├── ...
├── V11__Create_App_User.sql       # Security roles
├── V12__Multi_Tenant_Catalog.sql  # Multi-tenancy
├── V13__Audit_Events.sql          # Audit trail
└── V14__Retention_Policies.sql    # Data lifecycle
```

### Running Migrations

```bash
# Method 1: Manual (recommended for production)
for sql in src/main/resources/db/migration/V*.sql; do
    docker exec -i goodfellaz17-postgres psql \
        -U spotifybot_admin -d spotifybot < "$sql"
done

# Method 2: Flyway (CI/CD)
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/spotifybot
```

### Adding New Migrations

1. Create file: `V15__Your_Feature.sql`
2. Follow naming: `V<number>__<Description>.sql`
3. Test locally: `docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot < V15__Your_Feature.sql`
4. Update `tenant_databases.target_schema_version` to `V15`

---

## Security Configuration

### Two-Role Model

| Role | Purpose | Permissions |
|------|---------|-------------|
| `spotifybot_admin` | Migrations, backups | DDL + DML + SUPERUSER |
| `spotifybot_app` | Application runtime | DML only (SELECT, INSERT, UPDATE, DELETE) |

### Password Management

```bash
# Set admin password (in docker-compose.db.yml)
POSTGRES_PASSWORD: Mariogomez33Strong

# Set app password (via script)
./infra/setup-app-user.sh 'YourSecurePassword'

# Update PgBouncer userlist
vi infra/pgbouncer/userlist.txt
```

### Generating MD5 Hashes (for PgBouncer)

```bash
# Format: echo -n "passwordusername" | md5sum
echo -n "SpotifyApp2026Securespotifybot_app" | md5sum
# Output: abc123... (use as md5abc123...)
```

---

## Backup & Recovery

### Automated Backups

```bash
# Daily backup (add to crontab)
0 3 * * * /path/to/goodfellaz17/infra/backup-db.sh

# Manual backup
./infra/backup-db.sh
```

### Restore from Backup

```bash
# List available backups
ls -la infra/backups/

# Restore specific backup
./infra/restore-db.sh infra/backups/spotifybot_20260119_030000.sql.gz
```

### Backup Strategy

| Data Type | Retention | Method |
|-----------|-----------|--------|
| Full backup | 30 days | Daily pg_dump |
| Transaction logs | 7 days | WAL archiving (future) |
| Audit events | 2 years | In-database + archive |

---

## Multi-Tenancy

### Overview

Multi-tenancy allows running multiple SMM panels on the same infrastructure:

```
┌─────────────────────────────────────────────────────────────┐
│                    SHARED INFRASTRUCTURE                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐    │
│  │ goodfellaz17│  │  botzzz773  │  │   newsite_db    │    │
│  │  (spotifybot)│  │ (botzzz773_db)│  │   (future)      │    │
│  └─────────────┘  └─────────────┘  └─────────────────┘    │
│         ▲                ▲                  ▲              │
│         └────────────────┴──────────────────┘              │
│                          │                                  │
│              ┌───────────▼───────────┐                     │
│              │   DatasourceRouter    │                     │
│              │   (tenant_databases)  │                     │
│              └───────────────────────┘                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Enabling Multi-Tenancy

```yaml
# application-local-selfhosted.yml
goodfellaz17:
  multitenancy:
    enabled: true
    default-tenant: goodfellaz17
```

### Adding a New Tenant

See [MULTI_TENANT_GUIDE.md](MULTI_TENANT_GUIDE.md) for detailed instructions.

Quick version:
```bash
# 1. Create tenant database
./infra/branch-db.sh botzzz773

# 2. Run migrations
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot_botzzz773 \
    < src/main/resources/db/migration/V1__Initial_Schema.sql
# ... repeat for all migrations

# 3. Register in catalog (done by branch-db.sh)
# 4. Configure API key routing
```

---

## Monitoring & Health Checks

### Health Endpoints

```bash
# Full health status
curl http://localhost:8080/actuator/health | jq

# Database-specific health
curl http://localhost:8080/actuator/health/db | jq
```

### Health Response Example

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "latencyMs": 23,
        "consecutiveFailures": 0,
        "lastCheckTime": "2026-01-19T17:31:51.906Z",
        "thresholds": {
          "upThresholdMs": 100,
          "degradedThresholdMs": 500,
          "maxConsecutiveFailures": 3
        }
      }
    }
  }
}
```

### Status Definitions

| Status | Condition | Action |
|--------|-----------|--------|
| **UP** | latency < 100ms | Normal operation |
| **DEGRADED** | 100ms ≤ latency ≤ 500ms | Monitor closely |
| **DOWN** | latency > 500ms OR 3+ failures | Alert, investigate |

### PgBouncer Stats

```bash
# Connect to PgBouncer admin
docker exec -it goodfellaz17-pgbouncer psql -p 5432 -U spotifybot_admin pgbouncer

# Show pools
SHOW POOLS;

# Show stats
SHOW STATS;
```

---

## Troubleshooting

### Connection Refused on Port 6432

```bash
# Check PgBouncer is running
docker logs goodfellaz17-pgbouncer

# Verify userlist.txt is correct
cat infra/pgbouncer/userlist.txt

# Restart PgBouncer
docker compose -f infra/docker-compose.db.yml restart pgbouncer
```

### "Password authentication failed"

```bash
# Regenerate MD5 hash
echo -n "YourPasswordspotifybot_app" | md5sum

# Update userlist.txt
echo '"spotifybot_app" "md5<hash>"' >> infra/pgbouncer/userlist.txt

# Restart PgBouncer
docker compose -f infra/docker-compose.db.yml restart pgbouncer
```

### Migrations Failing

```bash
# Check current schema version
docker exec -it goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
    -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Run single migration manually
docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
    < src/main/resources/db/migration/V12__Multi_Tenant_Catalog.sql
```

### High Latency in Health Check

```bash
# Check Postgres connections
docker exec -it goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
    -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"

# Check PgBouncer pool status
docker exec -it goodfellaz17-pgbouncer psql -p 5432 -U spotifybot_admin pgbouncer \
    -c "SHOW POOLS;"

# Check for long-running queries
docker exec -it goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
    -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query 
        FROM pg_stat_activity WHERE state = 'active' 
        ORDER BY duration DESC LIMIT 5;"
```

---

## Next Steps

1. **Phase 1 (This Week):** Multi-tenant catalog + PgBouncer ✅
2. **Phase 2 (HP Omen):** Read replica + replication lag monitoring
3. **Phase 3 (VPS):** Automated failover + tenant quotas

See [BLOCKERS_AND_SOLUTIONS.md](BLOCKERS_AND_SOLUTIONS.md) for the full roadmap.
