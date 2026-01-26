# 15K Engine Freeze Specification

## Overview

This document defines the **freeze criteria** for the goodfellaz17 15k delivery engine. Once frozen, the engine's core logic (order creation, task generation, task execution, invariants) must not change. Only external integrations (real proxies, real Spotify APIs) will be added later.

---

## 1. Freeze Criteria

### 1.1 What "Frozen" Means

The 15k engine is considered **frozen** when:

1. **All invariants pass** for orders of any size (100, 1k, 5k, 15k)
2. **The torture test passes** 3 consecutive runs without failures
3. **No in-memory mocks** for persistent state (real Postgres used)
4. **The control flow is identical to production** (only external calls are stubbed)

### 1.2 Invariants That Must Pass

| ID | Name | Description | Check |
|----|------|-------------|-------|
| INV-1 | QUANTITY_ACCOUNTING | `sum(COMPLETED task qty) + failedPermanentPlays == order.quantity` | Endpoint: `/api/admin/invariants/order/{id}` |
| INV-2 | NO_STALE_EXECUTING | No EXECUTING task older than orphan threshold (120s prod, 30s dev) | Endpoint: `/api/admin/invariants/orphans` |
| INV-3 | COMPLETION_IMPLIES_TERMINAL | COMPLETED order means all tasks are COMPLETED or FAILED_PERMANENT | Checked on order completion |
| INV-4 | TASK_IDEMPOTENCY | Each task's idempotencyToken is unique within the order | DB constraint |

### 1.3 What Counts as "No Mocks"

| Component | Must Be Real | Can Be Stubbed |
|-----------|--------------|----------------|
| PostgreSQL database | ✅ Yes | ❌ No |
| Order/OrderTask/BalanceTransaction persistence | ✅ Yes | ❌ No |
| Task generation algorithm | ✅ Yes | ❌ No |
| Task claiming and state machine | ✅ Yes | ❌ No |
| Worker scheduling and pickup | ✅ Yes | ❌ No |
| Orphan recovery | ✅ Yes | ❌ No |
| Proxy selection (HybridProxyRouterV2) | ✅ Yes | ❌ No |
| **Actual play delivery to Spotify** | ❌ No | ✅ Yes |
| **Real proxy network calls** | ❌ No | ✅ Yes |

---

## 2. External Wiring Contract

When real API keys and credentials arrive, the following components will be wired:

### 2.1 Environment Variables Needed Later

```bash
# === DATABASE (already configured) ===
NEON_HOST=your-db.neon.tech
NEON_DB=goodfellaz17
NEON_USER=your-user
NEON_PASSWORD=your-password
NEON_SSLMODE=require

# === PROXY PROVIDERS (to be added) ===
BRIGHTDATA_ENABLED=true
BRIGHTDATA_USER=brd-customer-XXX
BRIGHTDATA_PASSWORD=XXX
BRIGHTDATA_ZONE=residential

SOAX_ENABLED=true
SOAX_USER=your-soax-user
SOAX_PASSWORD=your-soax-password

# === TOR NETWORK (optional, free) ===
TOR_ENABLED=true
TOR_HOST=127.0.0.1
TOR_PORTS=9050,9051,9052

# === SPOTIFY INTEGRATION (future) ===
SPOTIFY_CLIENT_ID=your-client-id
SPOTIFY_CLIENT_SECRET=your-client-secret
```

### 2.2 Components That Will Use Real Credentials

| Component | File | What Changes |
|-----------|------|--------------|
| `HybridProxyRouterV2` | `infrastructure/proxy/generated/HybridProxyRouterV2.java` | Will connect to real proxy providers |
| `BrightDataProxyAdapter` | `infrastructure/proxy/BrightDataProxyAdapter.java` | Real BrightData API calls |
| `SoaxProxyAdapter` | `infrastructure/proxy/SoaxProxyAdapter.java` | Real SOAX API calls |
| `PlayDeliveryExecutor` | `application/worker/PlayDeliveryExecutor.java` (to be created) | Real Spotify play delivery |

### 2.3 The Guarantee

**Adding real credentials and implementing real external calls MUST NOT change:**

1. ❌ `OrderEntity` schema
2. ❌ `OrderTaskEntity` schema
3. ❌ `BalanceTransactionEntity` schema
4. ❌ Any of the 4 invariants
5. ❌ Public API contract (`POST /api/v2/orders`, `GET /api/v2/orders/{id}`)
6. ❌ Task state machine (PENDING → EXECUTING → COMPLETED/FAILED_*)

**The only change will be:**
- `executeDelivery()` in `OrderDeliveryWorker` switches from simulated delay to real proxy+Spotify calls
- This is controlled by a `PlayDeliveryExecutor` interface with `StubPlayDeliveryExecutor` (current) and `RealPlayDeliveryExecutor` (future) implementations

---

## 3. Dev Shortcuts Identified and Controlled

### 3.1 Instant Execution Shortcut

**Location:** `OrderExecutionService.shouldInstantExecute()`

**Behavior:** Orders ≤1000 plays in local/dev profiles are marked COMPLETED immediately, skipping task generation.

**Control Flag:** `goodfellaz17.delivery.force-task-delivery`
- `false` (default): Instant execution enabled for small orders
- `true` (freeze test): All orders use task-based delivery

### 3.2 Time Compression

**Location:** `TaskGenerationService.getEffectiveTimeMultiplier()`

**Behavior:** In dev mode, time is compressed 720x (72 hours → 6 minutes).

**Control Flag:** `goodfellaz17.delivery.time-multiplier`
- `720` (local): Fast testing
- `1.0` (prod): Real timing

### 3.3 Simulated Delivery

**Location:** `OrderDeliveryWorker.executeDelivery()`

**Behavior:** Returns success after 500ms delay instead of real Spotify calls.

**Future Change:** Will delegate to `PlayDeliveryExecutor` interface.

---

## 4. Freeze Checklist

Run this checklist before declaring the engine frozen:

### Prerequisites
```bash
# 1. Ensure Postgres is running
docker-compose up -d postgres
# OR
brew services start postgresql@15

# 2. Verify database
psql postgresql://goodfellaz17:localdev123@localhost:5432/goodfellaz17 -c "SELECT 1;"

# 3. Start app with local profile
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests
```

### Test Runs (Must Pass 3 Consecutive Times)

```bash
# Quick mode (2k order, ~3 min)
./scripts/15k-freeze-test.sh --quick

# Full mode (15k order, ~10 min)
./scripts/15k-freeze-test.sh --full

# With restart (includes app restart mid-execution)
./scripts/15k-freeze-test.sh --quick --restart

# With chaos (failure injection enabled)
./scripts/15k-freeze-test.sh --quick --chaos
```

### Verification Points

- [ ] Order status reaches COMPLETED
- [ ] `delivered + failedPermanent == quantity` (INV-1)
- [ ] No orphaned EXECUTING tasks (INV-2)
- [ ] All tasks are terminal (INV-3)
- [ ] Exit code is 0

### After Passing 3 Consecutive Runs

```
✅ 15k Engine is FROZEN

Future changes allowed:
- Add real proxy provider implementations
- Add real Spotify delivery implementation
- Add new monitoring/metrics

Future changes NOT allowed without re-running freeze test:
- Modify Order/OrderTask/BalanceTransaction schemas
- Modify task state machine
- Modify invariant definitions
- Modify public API contract
```

---

## 5. Additional Tests for Live Integration (Future)

When wiring real credentials, add these tests:

1. **Latency bounds test**: Real proxy calls must complete within 30s
2. **Rate limit handling**: Must gracefully handle proxy provider rate limits
3. **Failover test**: If primary proxy provider fails, fallback to secondary
4. **Cost tracking test**: Verify proxy costs are tracked per order
5. **Spotify validation**: Verify actual play counts increase on Spotify

---

## 6. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PUBLIC API                                   │
│  POST /api/v2/orders  │  GET /api/v2/orders/{id}  │  GET /balance   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    OrderExecutionService                             │
│  - Validates input                                                   │
│  - Deducts balance (atomic)                                          │
│  - Creates Order entity                                              │
│  - Triggers TaskGenerationService for orders > threshold             │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    TaskGenerationService                             │
│  - Calculates task count and schedule                                │
│  - Creates OrderTaskEntity records in PENDING state                  │
│  - Spreads tasks across delivery window (48-72h)                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    OrderDeliveryWorker                               │
│  @Scheduled - Runs every 10s (1s in dev)                             │
│  - Picks up PENDING tasks past scheduled_at                          │
│  - Picks up FAILED_RETRYING tasks past retry_after                   │
│  - Recovers orphaned EXECUTING tasks                                 │
│  - Claims task atomically (worker_id, execution_started_at)          │
│  - Selects proxy via HybridProxyRouterV2                             │
│  - Executes delivery (STUB NOW, REAL LATER)  ◄── WIRING POINT       │
│  - Updates task status (COMPLETED or FAILED_*)                       │
│  - Updates order.delivered                                           │
│  - Marks order COMPLETED when all tasks terminal                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    OrderInvariantValidator                           │
│  - Validates INV-1 through INV-4                                     │
│  - Called after order completion                                     │
│  - Exposed via /api/admin/invariants/* endpoints                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 7. File Reference

| File | Purpose |
|------|---------|
| `OrderExecutionService.java` | Order creation, balance deduction |
| `TaskGenerationService.java` | Task schedule calculation and creation |
| `OrderDeliveryWorker.java` | Task pickup, execution, completion |
| `OrderInvariantValidator.java` | Invariant checks |
| `OrderEntity.java` | Order aggregate root |
| `OrderTaskEntity.java` | Task entity with state machine |
| `BalanceTransactionEntity.java` | Financial ledger |
| `application-local.yml` | Dev configuration with shortcuts |
| `15k-freeze-test.sh` | Self-contained freeze verification |

---

*Last updated: January 2026*
*Engine version: 1.0.0-SNAPSHOT*
