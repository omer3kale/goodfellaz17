# GoodFellaz17 – PostgreSQL Connection Patterns & Incident Playbook

**Date:** 2026-01-26
**Context:** Thesis phase 3 infrastructure. Resolves repeated database connectivity issues on macOS + Colima.

---

## 1. Robust Design Patterns (Never Experience This Again)

### 1.1 Port Mapping: Use Non-Standard Host Port

**Problem:** macOS often has Homebrew PostgreSQL on 5432, colliding with Docker's standard mapping.

```yaml
# ✗ WRONG: Container 5432 → host 5432 (collides with Homebrew)
ports:
  - "5432:5432"

# ✓ CORRECT: Container 5432 → host 55432 (avoids collision)
ports:
  - "55432:5432"
```

**Verify mapping:**
```bash
docker port goodfellaz17-postgres
# Output should show: 5432/tcp -> 0.0.0.0:55432
```

---

### 1.2 Credentials: Single Source of Truth

**Pattern:** docker-compose.local.yml defines all credentials; app config reads the same values.

```yaml
# docker-compose.local.yml
services:
  postgres:
    environment:
      POSTGRES_DB: goodfellaz17
      POSTGRES_USER: goodfellaz17
      POSTGRES_PASSWORD: Mariogomez33Strong
      POSTGRES_HOST_AUTH_METHOD: trust
```

```yaml
# application-local.yml (matches above)
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:55432/goodfellaz17
    username: goodfellaz17
    password: Mariogomez33Strong
```

**Never:** Manually `ALTER USER` during development. If you need to change password, update compose and destroy the volume:

```bash
docker rm -f goodfellaz17-postgres
docker volume rm goodfellaz17_postgres_data
docker-compose -f docker-compose.local.yml up -d postgres
```

---

### 1.3 Database Access: Prefer `docker exec` for Scripts & Tools

**Problem:** Colima's host→VM port forwarding is the most fragile part of the stack. When it breaks, you have no visibility into why (network layer, not Postgres layer).

**Pattern:** All scripts, chaos tests, and manual queries use container-native access:

```bash
# Container-native (always reliable)
docker exec goodfellaz17-postgres psql -U goodfellaz17 -d goodfellaz17 -c "SELECT 1"

# Host access (best-effort, may break if Colima forwarding flakes)
psql -h 127.0.0.1 -p 55432 -U goodfellaz17 -d goodfellaz17 -c "SELECT 1"
```

**Use the provided helper:**

```bash
# Source once in your shell session
source scripts/dbpsql.sh

# Now use:
dbpsql -c "SELECT COUNT(*) FROM orders;"
dbconn "SELECT NOW();"
dbhealth  # check container and DB status
```

This helper uses `docker exec` internally, avoiding port forwarding entirely.

---

### 1.4 Spring Boot: Two Options

#### Option A – Run Spring Boot from Host (Less Robust)

- App code runs on macOS.
- Connects to Postgres via host port 55432.
- If Colima port forwarding breaks, app cannot start.

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

**Risk:** You're now dependent on Colima's forwarding layer for your critical development loop.

#### Option B – Run Spring Boot in Docker (Recommended)

- Both Postgres and app run in same docker-compose.local.yml.
- App connects to Postgres via service name: `postgres:5432` (internal Docker network, no forwarding).
- Only `8080` (app) and `55432` (optional host tools) hit the forwarding layer.

```yaml
# docker-compose.local.yml
services:
  postgres: ...

  app:
    build: .
    depends_on:
      - postgres
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: r2dbc:postgresql://postgres:5432/goodfellaz17
      SPRING_DATASOURCE_USERNAME: goodfellaz17
      SPRING_DATASOURCE_PASSWORD: Mariogomez33Strong
    ports:
      - "8080:8080"
```

Then:

```bash
docker-compose -f docker-compose.local.yml up app postgres
curl http://localhost:8080/api/health
```

**Advantage:** Even if host port forwarding breaks, the container network keeps app ↔ DB connection alive. Thesis infrastructure is resilient.

---

## 2. Incident Playbook: If Something Breaks

When you hit a DB connection issue, follow this exact sequence. **Do not skip steps.**

### Step 1: Is the Container Running?

```bash
docker ps | grep goodfellaz17-postgres || echo "NOT RUNNING"
```

- If container is not listed → **start it:** `docker-compose -f docker-compose.local.yml up -d postgres`

- If container is listed → **check health:**

```bash
docker logs --tail 20 goodfellaz17-postgres | grep -i "error\|failed\|fatal"
```

Common errors:

- `failed to initialize database` → Volume is corrupted. Destroy and recreate:
  ```bash
  docker rm -f goodfellaz17-postgres
  docker volume rm goodfellaz17_postgres_data
  docker-compose -f docker-compose.local.yml up -d postgres
  ```

- `password authentication failed` → See Step 4 (credentials).

- `bind: address already in use` → Another Postgres on 55432. Check: `lsof -i :55432`

---

### Step 2: Is Postgres Healthy **Inside** the Container?

```bash
docker exec goodfellaz17-postgres psql -U goodfellaz17 -d goodfellaz17 -c "SELECT 'Inside container OK' as status, now() as time;"
```

- ✓ **Returns data:** Postgres is fine. Problem is external (networking, credentials, Colima forwarding).
- ✗ **Connection refused or error:** Postgres is crashed inside the container. Check logs from Step 1.

---

### Step 3: Is the Port Mapping Correct?

```bash
docker port goodfellaz17-postgres
```

Expected output:

```
5432/tcp -> 0.0.0.0:55432
```

- ✗ If port mapping is missing or wrong → Fix docker-compose.local.yml and restart: `docker-compose -f docker-compose.local.yml down && docker-compose -f docker-compose.local.yml up -d postgres`

---

### Step 4: Can the Host Reach the Mapped Port?

```bash
nc -zv 127.0.0.1 55432
```

- ✓ **Output:** `Connection succeeded` → Port forwarding is working.
- ✗ **Output:** `Connection refused` or timeout → **Colima port forwarding is broken**. Fix:

```bash
colima stop
colima start --network-address
# Wait ~30s for startup
docker-compose -f docker-compose.local.yml up -d postgres
```

This is *not* a Postgres problem. Colima's VM bridge layer has stalled. Restarting it fixes it.

---

### Step 5: Credentials & Authentication

Once `nc -zv 127.0.0.1 55432` succeeds, try the actual connection:

```bash
PGPASSWORD="Mariogomez33Strong" psql -h 127.0.0.1 -p 55432 -U goodfellaz17 -d goodfellaz17 -c "SELECT 'Authenticated!' as status;"
```

- ✓ **Returns data:** Everything works. Move on.

- ✗ **`password authentication failed`:** Inside container is fine (we verified in Step 2), so this means:
  - You're hitting a different Postgres (unlikely if `nc -zv` worked).
  - The password inside the container doesn't match what you're using. Set it:

```bash
docker exec goodfellaz17-postgres psql -U goodfellaz17 -c "ALTER USER goodfellaz17 WITH PASSWORD 'Mariogomez33Strong';"
docker exec goodfellaz17-postgres psql -U goodfellaz17 -d goodfellaz17 -c "SELECT 1;"
```

Then retry from host.

- ✗ **`connection refused`:** But `nc -zv` worked? This is rare. It usually means:
  - You have `pg_hba.conf` set to reject your source IP for some reason.
  - Restart the container and see if it clears: `docker restart goodfellaz17-postgres`

---

## 3. Decision Tree

```
┌─────────────────────────────────────────────────────────────────┐
│  DB CONNECTION ISSUE ON macOS + COLIMA                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┴────────────────┐
         │                                │
    ┌────▼────┐                      ┌────▼──────┐
    │ Ask: Is │                      │ Ask: Does │
    │container│                      │  docker   │
    │running? │                      │  exec     │
    │         │                      │ psql work?│
    └────┬────┘                      └─────┬─────┘
         │ NO → START CONTAINER            │ NO
         │        (see BACKUP_STRATEGY)    │ ├─ Postgres CRASHED inside
         │                                 │ └─ Blow away volume & restart
         │ YES ─────────────────┐          │
         │                      │          │ YES ──────────┐
         └──────────────────────┼──────────┼──────┐        │
                                │          │      │        │
                        ┌───────▼──────┐   │      │        │
                        │ Ask: Can host│   │      │        │
                        │ port forward?│   │      │        │
                        │ (nc -zv)     │   │      │        │
                        └───────┬──────┘   │      │        │
                                │          │      │        │
                    ┌───────────┴────┐     │      │        │
                    │ NO             │YES  │      │        │
                    │ └─ Restart     │     │      │        │
                    │   Colima       │     │      │        │
                    │                └─┐   │      │        │
                    │                  │   │      │        │
                    │  ┌──────────┐    │   │      │        │
                    │  │Try psql  │◄───┘   │      │        │
                    │  │from host │        │      │        │
                    │  └────┬─────┘        │      │        │
                    │       │            ┌─┘      │        │
                    │  ┌────▼───┐        │        │        │
                    │  │Works?  │        │        │        │
                    │  └────┬───┘        │        │        │
                    │       │            │        │        │
                    │    YES│NO          │        │        │
                    │       │            │        │        │
                    │       └────┐   ┌───┘        │        │
                    │            │   │ USE:       │        │
                    │            │   │ dbpsql     │        │
                    │            │   │ (docker    │        │
                    │            │   │  exec)    │        │
                    │        ┌───┴───▼───────┐   │        │
                    │        │ Check cred &  │   │        │
                    │        │ pg_hba.conf   │   │        │
                    │        │ (see Step 5)  │   │        │
                    │        └───────────────┘   │        │
                    │                            │        │
                    └────────────────┬───────────┴────────┘
                                     │
                              ✓ ALL WORKING
```

---

## 4. Quick Reference Commands

```bash
# Source helper functions (do once per shell session)
source scripts/dbpsql.sh

# Full diagnostics
dbhealth

# Manual container access (if dbpsql not sourced)
docker exec goodfellaz17-postgres psql -U goodfellaz17 -d goodfellaz17

# Check port mapping
docker port goodfellaz17-postgres

# Test host connectivity
nc -zv 127.0.0.1 55432

# Check Colima status
colima status

# Restart Colima (nuclear option)
colima stop && colima start --network-address

# Destroy and recreate Postgres
docker rm -f goodfellaz17-postgres && \
docker volume rm goodfellaz17_postgres_data && \
docker-compose -f docker-compose.local.yml up -d postgres

# View container logs
docker logs --tail 100 goodfellaz17-postgres

# Run Spring Boot from Docker (once `app` service added to compose)
docker-compose -f docker-compose.local.yml up app postgres

# Run Spring Boot from host (requires working host→55432 connectivity)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

---

## 5. What We Fixed to Avoid This

| Issue | Solution | Files Changed |
|-------|----------|----------------|
| Port collision with Homebrew Postgres | Use 55432:5432 instead of 5432:5432 | `docker-compose.local.yml` |
| Password mismatch | Single source of truth in docker-compose, matched in application-local.yml | `docker-compose.local.yml`, `application-local.yml` |
| Manual auth fumbling | Never use `ALTER USER` during dev; blow away volume if needed | Procedural / documented |
| Fragile host→VM forwarding | Prefer `docker exec` for scripts; added `dbpsql.sh` helper | `scripts/dbpsql.sh` |
| Thesis script breakage | `thesis_evidence.sh` already uses `docker exec psql` ✓ | No change needed |

---

## 6. For Thesis Phase 3 Going Forward

1. **All DB queries in scripts:** Use `dbpsql` or `docker exec psql`, never host `psql`.
2. **Credentials:** Update docker-compose.local.yml and application-local.yml in lock-step.
3. **If Colima breaks:** Quick diagnostic loop is: container running → inside psql works → check nc → restart Colima. Takes 2 min, solves 99% of failures.
4. **Spring Boot:**
   - Option A (simple): Run on host with `mvn spring-boot:run`, use 55432 connection. Fragile if Colima flakes.
   - Option B (robust): Add `app` service to docker-compose.local.yml, run entire stack in Docker. Zero host dependency.

---

**TL;DR:** Use 55432:5432, match credentials in compose + app config, prefer `docker exec` for scripts, and you won't spend hours on this class of problem again.
