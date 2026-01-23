# GOODFELLAZ17 - Backup & Recovery Strategy

## Overview

This document describes the backup strategy for the self-hosted Goodfellaz17 PostgreSQL database, designed to replicate Neon's automated backup and point-in-time recovery (PITR) capabilities.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    BACKUP ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐    pg_dump    ┌────────────────────────┐ │
│  │ goodfellaz17-    │ ──────────────►│ spotifybot_YYYYMMDD_  │ │
│  │ postgres         │    + gzip      │ HHMMSS.sql.gz         │ │
│  │ (Docker)         │               └────────────────────────┘ │
│  └──────────────────┘                          │               │
│           │                                    │               │
│           │                          ┌─────────▼─────────┐     │
│           │                          │ infra/backups/    │     │
│           │                          │ (30-day retention)│     │
│           │                          └───────────────────┘     │
│           │                                    │               │
│           │ restore                            │ rotate        │
│           ▼                                    ▼               │
│  ┌──────────────────┐               ┌───────────────────┐     │
│  │ Fresh Schema     │               │ Auto-delete >30d  │     │
│  │ + Data           │               │ via cron          │     │
│  └──────────────────┘               └───────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Backup Schedule

| Frequency | Time  | Retention | Storage     |
|-----------|-------|-----------|-------------|
| Daily     | 03:00 | 30 days   | Local disk  |

### Why 30 Days?

1. **Compliance**: Most data retention policies require 30+ days
2. **Recovery Window**: Covers monthly billing cycles and audit periods
3. **Storage Balance**: ~30 backups × ~10MB = ~300MB total (manageable)
4. **Incident Detection**: Most issues are discovered within 30 days

## Backup Process

### Daily Backup (`backup-db.sh`)

```bash
# Manual run
./infra/backup-db.sh

# Automated via cron (see below)
```

**What happens:**

1. **Preflight checks**: Docker running, container healthy, disk space
2. **pg_dump**: Full database dump in plain SQL format
3. **Compression**: gzip -9 (maximum compression)
4. **Verification**: gzip integrity test
5. **Rotation**: Delete backups older than 30 days
6. **Logging**: All operations logged to `infra/backup.log`

**Output format:**
```
infra/backups/spotifybot_20260119_030000.sql.gz
infra/backups/spotifybot_20260120_030000.sql.gz
...
```

## Recovery Process

### Point-in-Time Restore (`restore-db.sh`)

```bash
# List available backups
ls -la infra/backups/

# Restore from specific backup
./infra/restore-db.sh infra/backups/spotifybot_20260119_030000.sql.gz
```

**What happens:**

1. **Validation**: Verify backup file exists and is valid gzip
2. **Safety backup**: Create pre-restore snapshot (rollback point)
3. **Confirmation**: User must type 'RESTORE' to proceed
4. **Schema reset**: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
5. **Restore**: gunzip | psql with transaction safety
6. **Verification**: Check table count and basic integrity
7. **Optional migrations**: Run V1-V10 to ensure schema consistency

### Recovery Time Objective (RTO)

| Operation          | Estimated Time |
|--------------------|----------------|
| Backup restore     | 30-60 seconds  |
| Migration re-run   | 10-20 seconds  |
| Verification       | 5 seconds      |
| **Total RTO**      | **~2 minutes** |

### Recovery Point Objective (RPO)

| Scenario              | Data Loss Window |
|-----------------------|------------------|
| Daily backup          | Up to 24 hours   |
| With WAL archiving*   | Minutes          |

*WAL archiving not implemented in current setup

## Crontab Configuration

### Add to crontab:

```bash
# Edit crontab
crontab -e

# Add this line for daily 3 AM backup:
0 3 * * * /Users/omer3kale/Desktop/goodfellaz17/infra/backup-db.sh >> /Users/omer3kale/Desktop/goodfellaz17/infra/cron.log 2>&1
```

### Full crontab entry with log rotation:

```bash
# Goodfellaz17 PostgreSQL Backup - Daily at 3 AM
0 3 * * * /Users/omer3kale/Desktop/goodfellaz17/infra/backup-db.sh >> /Users/omer3kale/Desktop/goodfellaz17/infra/cron.log 2>&1

# Log rotation - Weekly on Sunday at 4 AM
0 4 * * 0 find /Users/omer3kale/Desktop/goodfellaz17/infra -name "*.log" -size +10M -exec truncate -s 0 {} \;
```

## Failover Scenarios

### Scenario 1: Corrupted Data (User Error)

**Problem**: Accidental DELETE, wrong UPDATE, schema change gone wrong

**Solution**:
```bash
# 1. Stop the application
pkill -f "spring-boot:run"

# 2. Identify the last good backup
ls -la infra/backups/

# 3. Restore from backup
./infra/restore-db.sh infra/backups/spotifybot_20260118_030000.sql.gz

# 4. Restart application
SPRING_PROFILES_ACTIVE=local-selfhosted mvn spring-boot:run
```

### Scenario 2: Container Crash

**Problem**: Docker container dies, data volume corrupted

**Solution**:
```bash
# 1. Remove corrupted container and volume
docker compose -f infra/docker-compose.db.yml down -v
rm -rf infra/data/postgres

# 2. Start fresh container
docker compose -f infra/docker-compose.db.yml up -d

# 3. Wait for container to be healthy
sleep 10

# 4. Restore from backup
./infra/restore-db.sh infra/backups/spotifybot_20260119_030000.sql.gz
```

### Scenario 3: Host Machine Failure

**Problem**: Server dies, need to restore on new machine

**Solution**:
1. Copy `infra/backups/` directory to new machine
2. Clone repository, set up Docker
3. Start fresh Postgres container
4. Run restore script with latest backup

### Scenario 4: Migration to Cloud (Neon/RDS)

**Problem**: Moving from self-hosted to managed database

**Solution**:
```bash
# 1. Create final backup
./infra/backup-db.sh

# 2. Decompress for cloud import
gunzip -c infra/backups/spotifybot_20260119_030000.sql.gz > /tmp/spotifybot.sql

# 3. Import to cloud database
psql "postgres://user:pass@cloud-host:5432/spotifybot" < /tmp/spotifybot.sql
```

## Backup Verification

### Monthly Verification Checklist

```bash
# 1. List backups and verify count (should be ~30)
ls infra/backups/*.sql.gz | wc -l

# 2. Check backup sizes (should be consistent)
du -sh infra/backups/

# 3. Test restore to verify backups work
# Create a test database
docker exec goodfellaz17-postgres createdb -U spotifybot_admin spotifybot_test

# Restore to test database
gunzip -c infra/backups/spotifybot_$(date +%Y%m%d)_*.sql.gz | \
  docker exec -i goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot_test

# Verify data
docker exec goodfellaz17-postgres psql -U spotifybot_admin -d spotifybot_test \
  -c "SELECT count(*) FROM services;"

# Cleanup test database
docker exec goodfellaz17-postgres dropdb -U spotifybot_admin spotifybot_test
```

## Storage Estimates

| Database Size | Compressed Backup | 30-Day Storage |
|---------------|-------------------|----------------|
| 10 MB         | ~2 MB             | ~60 MB         |
| 100 MB        | ~20 MB            | ~600 MB        |
| 1 GB          | ~200 MB           | ~6 GB          |

## Monitoring

### Check backup health:

```bash
# Last backup timestamp
ls -lt infra/backups/*.sql.gz | head -1

# Backup log (last 20 lines)
tail -20 infra/backup.log

# Check for errors
grep -i error infra/backup.log | tail -10
```

### Alerting (Future Enhancement)

Consider adding:
- Slack/Discord webhook on backup failure
- Email notification if no backup in 48 hours
- Prometheus metrics for backup size/duration

## Security Considerations

1. **Backup files contain sensitive data** - ensure proper file permissions
2. **Password in scripts** - consider using environment variables
3. **Backup directory** - should not be publicly accessible
4. **Off-site copies** - consider syncing to cloud storage (S3, B2, etc.)

### Recommended permissions:

```bash
chmod 700 infra/backup-db.sh
chmod 700 infra/restore-db.sh
chmod 700 infra/backups/
chmod 600 infra/backups/*.sql.gz
```

## Comparison: Self-Hosted vs Neon

| Feature               | This Setup        | Neon              |
|-----------------------|-------------------|-------------------|
| Automated backups     | ✅ Daily          | ✅ Continuous     |
| Point-in-time recovery| ✅ Daily snapshots| ✅ Any second     |
| Retention             | 30 days           | 7-30 days (plan)  |
| WAL archiving         | ❌ Not implemented| ✅ Built-in       |
| Storage cost          | Local disk        | Included          |
| Management            | Self-managed      | Fully managed     |
| Branching             | ❌                | ✅                |

## Quick Reference

```bash
# Create backup now
./infra/backup-db.sh

# Restore from backup
./infra/restore-db.sh infra/backups/spotifybot_YYYYMMDD_HHMMSS.sql.gz

# List backups
ls -lh infra/backups/

# Check backup log
tail -50 infra/backup.log

# Verify backup integrity
gzip -t infra/backups/spotifybot_*.sql.gz && echo "All backups OK"
```

---

*Last updated: January 19, 2026*
*Version: 1.0.0*
