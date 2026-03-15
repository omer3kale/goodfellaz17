# FTL-B MSSQL Foundation — Implementation Summary

**Date:** March 13, 2026 | **Status:** Core 3-table schema + Java models complete

## What's been created

### 1. MSSQL Migration (V15)
**File:** [`src/main/resources/db/migration/V15__Create_MSSQL_Core_Tables.sql`](src/main/resources/db/migration/V15__Create_MSSQL_Core_Tables.sql)

- **orders** table: Customer order for delivery (plays, followers, etc.)
  - Status progression: PENDING → ACTIVE → DELIVERING → COMPLETED/FAILED
  - Billing fields: estimated_cost, actual_cost
  - Progress tallies: plays_delivered, plays_failed

- **order_tasks** table: Individual task decomposed from an order
  - Status progression: PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED
  - Account + proxy locking on ASSIGNED state
  - Retry tracking (retry_count, max_retries, idempotency_key)
  - Timeout detection (stale_after)

- **execution_results** table: Outcome of each task execution attempt
  - Success/failure flags with reason tracking
  - Timing metrics (duration_ms, started_at, completed_at)
  - Detection signals (JSON) for FTL-X abuse pattern detection
  - Revenue impact for billing aggregation

### 2. Java Domain Models
- **Order.java**: Aggregate root with state transitions enforcing INV-2/3
- **OrderTask.java**: Task with state machine enforcing INV-4/5/6/7
- **ExecutionResult.java**: Read-only result with factories and detection signal recording

## Invariants Enforced

| Invariant | Description | Enforcer |
|-----------|-------------|----------|
| **INV-1** | order.quantity = SUM(order_tasks.quantity) | READ query contract, not DB constraint |
| **INV-2** | order.status progression: PENDING → ACTIVE → DELIVERING → COMPLETED/FAILED | Order class methods |
| **INV-3** | order terminal state ⟹ completed_at ≠ null | CHECK constraint + Order.complete()/fail() |
| **INV-4** | order_task.status progression: PENDING → ASSIGNED → EXECUTING → COMPLETED/FAILED | OrderTask class methods |
| **INV-5** | order_task.ASSIGNED ⟹ account_id ≠ null ∧ proxy_node_id ≠ null | CHECK constraint + OrderTask.assignTo() |
| **INV-6** | order_task.EXECUTING ⟹ started_at ≠ null | OrderTask.startExecution() |
| **INV-7** | order_task terminal ⟹ completed_at ≠ null | OrderTask.markCompleted()/markFailed() |
| **INV-8** | execution_result.started_at ≤ completed_at | CHECK constraint + factory methods |
| **INV-9** | execution_result.success_flag ∈ {0, 1} | BIT type |
| **INV-10** | success_flag=1 ⟹ failure_reason=NULL; success_flag=0 ⟹ failure_reason≠NULL | CHECK constraint + ExecutionResult factory |
| **INV-11** | execution_result.duration_ms ≥ 0 | CHECK constraint + ExecutionResult constructor |

## MSSQL Features

- ✅ **No PostgreSQL constructs**: No UUID extensions, JSONB, RLS policies, PL/pgSQL
- ✅ **MSSQL-native types**: UNIQUEIDENTIFIER, DATETIME2, NVARCHAR(MAX), BIT, DECIMAL
- ✅ **Clustered + non-clustered indexes**: Optimized for order polling, metric queries, detection filtering
- ✅ **CHECK constraints**: Enforce state machines and value ranges at DB layer
- ✅ **Denormalization for performance**: tenant_id in execution_results for isolation without joins
- ✅ **Default timestamps**: SYSDATETIME() for audit trail
- ✅ **Repeatable migrations**: Safe for multi-environment rollout

## How This Enables FTLs

| FTL | What this enables | Next step |
|-----|-------------------|-----------|
| **FTL-A** (Delivery Engine) | Task decomposition + execution state machine | Wire TaskExecutionService to new Order/OrderTask models; build ExecutionContext |
| **FTL-B** (MSSQL Schema) | Baseline for everything else | Add reference tables (tenants, services, price_tiers, accounts, proxy_nodes) |
| **FTL-C** (DDD) | Clear aggregate boundaries (Order + OrderTask = Delivery context) | Define OrderEventPublisher; partition Ordering vs Delivery contexts |
| **FTL-X** (Detection) | detection_signal + detection_score in execution_results | Build AnomalyService to score based on 10 signal categories |
| **FTL-D** (Infra/K8s) | Stateless task queue (order_tasks in PENDING status) | Task executor as stateless K8s pod; MSSQL as out-of-cluster state |
| **FTL-E** (Proxy Ops) | proxy_node_id FK + proxy_health tracking | DigitalOcean droplet lifecycle + heartbeat to proxy_health table |

## Queries this schema supports

### Billing/Payment
```sql
-- Monthly revenue by customer
SELECT tenant_id, SUM(actual_cost) AS revenue
FROM orders
WHERE created_at >= DATEADD(MONTH, -1, SYSDATETIME())
GROUP BY tenant_id;
```

### Metrics/SLO
```sql
-- Success rate by account (real-time)
SELECT account_id, 
       COUNT(*) AS attempts,
       SUM(CASE WHEN success_flag=1 THEN 1 ELSE 0 END) AS successes,
       CAST(SUM(CASE WHEN success_flag=1 THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*) AS success_rate
FROM execution_results
WHERE created_at >= DATEADD(HOUR, -24, SYSDATETIME())
GROUP BY account_id;
```

### Detection (FTL-X)
```sql
-- High-risk execution attempts
SELECT id, order_id, detection_score, detection_signal
FROM execution_results
WHERE detection_score > 75
ORDER BY detection_score DESC;
```

### Task Retry Detection
```sql
-- Find stale tasks (hung in EXECUTING > 5 minutes)
SELECT id, assigned_at, stale_after
FROM order_tasks
WHERE status = 'EXECUTING'
  AND stale_after < SYSDATETIME();
```

## Next immediate steps

1. **Add reference tables** (FTL-B phase 2)
   - `tenants(id, name, api_key_hash, created_at)`
   - `services(id, name, type: PLAYS|FOLLOWERS|ENGAGEMENT, created_at)`
   - `price_tiers(id, service_id, tier: BASIC|PREMIUM|VIP, unit_cost, created_at)`
   - `accounts(id, tenant_id, account_identifier, platform: SPOTIFY|INSTAGRAM, state)`
   - `proxy_nodes(id, host, port, status: HEALTHY|DEGRADED|OFFLINE, success_rate, created_at)`

2. **Build Java R2DBC repositories** (FTL-A phase 1)
   - OrderRepository (save, findById, findByStatusAndTenant)
   - OrderTaskRepository (findPendingTasksByTenant, updateStatus)
   - ExecutionResultRepository (save, findByOrderTaskId)

3. **Integrate with TaskExecutionService**
   - Accept Order/OrderTask from new models
   - Write ExecutionResult after each attempt
   - Update order status based on task completion

## Production readiness checklist

- [ ] V15 migration passes on fresh MSSQL instance
- [ ] Java models compile and pass annotation validation
- [ ] R2DBC repositories implemented + integration tests green
- [ ] TaskExecutionService rewritten to use new Order/OrderTask models
- [ ] Billing aggregation queries validated for correctness
- [ ] Detection signal format documented (JSON schema)
- [ ] All 11 invariants validated via unit tests
- [ ] Load test: 10k orders, 100k tasks, 1M execution_results

---

**Authored by:** FTL-B Design | **Reviewed by:** [pending]
