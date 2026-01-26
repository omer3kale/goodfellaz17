# GOODFELLAZ17 - Database Security Roles

## Overview

This document describes the PostgreSQL role separation strategy for the Goodfellaz17 platform, designed to mirror Neon's "no superuser for application code" security model.

### Two-Role Architecture

| Role | Purpose | Used By |
|------|---------|---------|
| `spotifybot_admin` | DDL, migrations, backups, maintenance | DevOps, CI/CD pipelines, manual maintenance |
| `spotifybot_app` | DML only (runtime operations) | Spring Boot application |

```
┌─────────────────────────────────────────────────────────────────┐
│                    ROLE SEPARATION MODEL                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐             │
│  │ Spring Boot App  │         │ DevOps / Admin   │             │
│  │ (local-selfhosted)│         │ (maintenance)    │             │
│  └────────┬─────────┘         └────────┬─────────┘             │
│           │                            │                        │
│           │ spotifybot_app             │ spotifybot_admin       │
│           │ (DML only)                 │ (Full access)          │
│           │                            │                        │
│           ▼                            ▼                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL                            │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │              spotifybot database                 │    │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐           │    │   │
│  │  │  │ users   │ │ orders  │ │ proxies │  ...      │    │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘           │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Privileges Matrix

### spotifybot_admin (Administrative)

| Privilege | Granted | Notes |
|-----------|---------|-------|
| SUPERUSER | ❌ | Not required, use postgres for emergencies |
| CREATEDB | ✅ | Can create test databases |
| CREATEROLE | ✅ | Can manage roles |
| CREATE (DDL) | ✅ | Can create tables, indexes |
| ALTER (DDL) | ✅ | Can modify schema |
| DROP (DDL) | ✅ | Can delete objects |
| SELECT | ✅ | Full read access |
| INSERT | ✅ | Full write access |
| UPDATE | ✅ | Full modify access |
| DELETE | ✅ | Full delete access |
| TRUNCATE | ✅ | Fast table clearing |
| GRANT/REVOKE | ✅ | Can manage permissions |

**Use for:**
- Running migrations (`V1__...` through `V11__...`)
- Backup operations (`backup-db.sh`)
- Restore operations (`restore-db.sh`)
- Schema changes and maintenance
- Emergency incident response

### spotifybot_app (Application Runtime)

| Privilege | Granted | Notes |
|-----------|---------|-------|
| SUPERUSER | ❌ | Never |
| CREATEDB | ❌ | Cannot create databases |
| CREATEROLE | ❌ | Cannot manage roles |
| CREATE (DDL) | ❌ | Cannot create tables |
| ALTER (DDL) | ❌ | Cannot modify schema |
| DROP (DDL) | ❌ | Cannot delete objects |
| SELECT | ✅ | Read from all app tables |
| INSERT | ✅ | Insert into all app tables |
| UPDATE | ✅ | Modify rows in all app tables |
| DELETE | ✅ | Delete rows from all app tables |
| TRUNCATE | ❌ | Cannot mass-delete without logging |
| GRANT/REVOKE | ❌ | Cannot change permissions |

**Use for:**
- Spring Boot application runtime
- API request handling
- Order processing
- User authentication
- All normal application operations

## Tables & Access

### Application Tables (DML for spotifybot_app)

| Table | SELECT | INSERT | UPDATE | DELETE |
|-------|--------|--------|--------|--------|
| users | ✅ | ✅ | ✅ | ✅ |
| orders | ✅ | ✅ | ✅ | ✅ |
| order_tasks | ✅ | ✅ | ✅ | ✅ |
| services | ✅ | ✅ | ✅ | ✅ |
| proxy_nodes | ✅ | ✅ | ✅ | ✅ |
| proxy_metrics | ✅ | ✅ | ✅ | ✅ |
| premium_accounts | ✅ | ✅ | ✅ | ✅ |
| balance_transactions | ✅ | ✅ | ✅ | ✅ |
| tor_circuits | ✅ | ✅ | ✅ | ✅ |
| device_nodes | ✅ | ✅ | ✅ | ✅ |
| refund_events | ✅ | ✅ | ✅ | ✅ |
| refund_anomalies | ✅ | ✅ | ✅ | ✅ |
| invariant_check_log | ✅ | ✅ | ✅ | ✅ |

### Views (Read-Only for spotifybot_app)

| View | SELECT |
|------|--------|
| v_invariant_health | ✅ |
| v_proxy_selection | ✅ |

## Usage Rules

### ✅ DO

1. **Application code** → Always use `spotifybot_app`
2. **Migrations** → Always use `spotifybot_admin`
3. **Backups** → Always use `spotifybot_admin`
4. **Restores** → Always use `spotifybot_admin`
5. **Schema changes** → Always use `spotifybot_admin`

### ❌ DON'T

1. **Never** put `spotifybot_admin` credentials in application code
2. **Never** put `spotifybot_admin` credentials in `.yml` profiles used by the app
3. **Never** share `spotifybot_admin` password with non-administrative personnel
4. **Never** use `spotifybot_app` for migrations or maintenance

## Configuration Files

### Application (spotifybot_app)

**File:** `src/main/resources/application-local-selfhosted.yml`

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/spotifybot
    username: spotifybot_app
    password: ${SPOTIFYBOT_APP_PASSWORD:YourAppPassword}
```

### Backups (spotifybot_admin)

**File:** `infra/backup-db.sh`

Uses `spotifybot_admin` via Docker exec (no password in script).

### Docker Compose

**File:** `infra/docker-compose.db.yml`

Only defines `spotifybot_admin` (the owner/superuser for the database).

## Password Rotation

### Rotate spotifybot_app Password

```bash
# 1. Change the password
./infra/setup-app-user.sh 'NewStrongPassword123!'

# 2. Update application config
# Edit: src/main/resources/application-local-selfhosted.yml
# Or set: export SPOTIFYBOT_APP_PASSWORD='NewStrongPassword123!'

# 3. Restart the application
pkill -f spring-boot:run
SPRING_PROFILES_ACTIVE=local-selfhosted mvn spring-boot:run
```

### Rotate spotifybot_admin Password

```bash
# 1. Stop all admin operations (backups, migrations)

# 2. Change password in PostgreSQL
docker exec -it goodfellaz17-postgres psql -U postgres -c \
  "ALTER ROLE spotifybot_admin WITH PASSWORD 'NewAdminPassword';"

# 3. Update docker-compose.db.yml
# Edit: POSTGRES_PASSWORD: NewAdminPassword

# 4. Note: Container volume retains old password until recreated
# For immediate effect, you may need to:
docker compose -f infra/docker-compose.db.yml down
# (Don't use -v or you'll lose data!)
docker compose -f infra/docker-compose.db.yml up -d
```

## Incident Response

### If spotifybot_app is Compromised

**Severity: MEDIUM** - Attacker can read/modify data but cannot alter schema.

1. **Immediate:**
   ```bash
   # Rotate password immediately
   ./infra/setup-app-user.sh 'EmergencyNewPassword!'

   # Restart application with new password
   ```

2. **Investigate:**
   - Check application logs for unusual queries
   - Review `balance_transactions` for unauthorized transfers
   - Check `orders` for suspicious patterns
   - Audit `users` table for new admin accounts

3. **Remediate:**
   - Identify attack vector (leaked credentials, SQL injection, etc.)
   - Patch vulnerability
   - Consider restoring from backup if data integrity is questionable

### If spotifybot_admin is Compromised

**Severity: CRITICAL** - Attacker has full database control.

1. **Immediate:**
   ```bash
   # Stop all database connections
   docker stop goodfellaz17-postgres

   # This is a CRITICAL incident - escalate immediately
   ```

2. **Assess:**
   - Assume all data may be compromised
   - Assume schema may have been modified
   - Check for backdoor accounts or triggers

3. **Recover:**
   ```bash
   # Option A: If attack was recent and you have a known-good backup
   docker compose -f infra/docker-compose.db.yml down -v
   rm -rf infra/data/postgres
   docker compose -f infra/docker-compose.db.yml up -d
   ./infra/restore-db.sh infra/backups/<last-known-good>.sql.gz

   # Re-run V11 with new passwords
   docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot \
     < src/main/resources/db/migration/V11__Create_App_User.sql
   ./infra/setup-app-user.sh 'BrandNewSecurePassword!'
   ```

4. **Post-Incident:**
   - Full security audit
   - Review how admin credentials were exposed
   - Implement additional controls (IP whitelisting, MFA, etc.)

## Verification Commands

### Check Current Privileges

```bash
# List all grants for spotifybot_app
docker exec -it goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot -c "
SELECT grantee, table_name, privilege_type
FROM information_schema.table_privileges
WHERE grantee = 'spotifybot_app'
ORDER BY table_name, privilege_type;
"
```

### Test spotifybot_app Cannot DDL

```bash
# This should FAIL
docker exec -it goodfellaz17-postgres psql -U spotifybot_app -d spotifybot -c "
CREATE TABLE test_should_fail (id int);
"
# Expected: ERROR:  permission denied for schema public
```

### Test spotifybot_app Can DML

```bash
# This should SUCCEED
docker exec -it goodfellaz17-postgres psql -U spotifybot_app -d spotifybot -c "
SELECT count(*) FROM services;
"
```

## Quick Reference

| Task | User | Command |
|------|------|---------|
| Run application | `spotifybot_app` | `SPRING_PROFILES_ACTIVE=local-selfhosted mvn spring-boot:run` |
| Run migrations | `spotifybot_admin` | `docker exec -i ... psql -U spotifybot_admin < V11__...sql` |
| Create backup | `spotifybot_admin` | `./infra/backup-db.sh` |
| Restore backup | `spotifybot_admin` | `./infra/restore-db.sh <file>` |
| Rotate app password | `spotifybot_admin` | `./infra/setup-app-user.sh 'NewPassword'` |
| Schema changes | `spotifybot_admin` | Manual SQL or new migration |

---

*Last updated: January 19, 2026*
*Version: 1.0.0*
