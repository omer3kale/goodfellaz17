# 15K Order Delivery - Operations Runbook

**Version:** 1.0.0  
**Last Updated:** 2025-01-XX  
**On-Call Escalation:** #15k-delivery-alerts Slack channel

---

## Quick Reference Card

| Metric | Alert Threshold | Page? |
|--------|-----------------|-------|
| `delivery_dead_letter_queue` | > 50 | Yes |
| `delivery_tasks_executing` stuck > 5min | > 10 | Yes |
| `delivery_orphans_recovered_total` spike | > 20/hour | No |
| Order stuck in PROCESSING > 2h | Any | No |

---

## 1. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        15K ORDER FLOW                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   [Order API] ──► [TaskGenerationService] ──► [order_tasks table]   │
│                        │                              │              │
│                   (generates 38                  (PENDING)           │
│                    tasks of ~400                     │               │
│                    plays each)                       ▼               │
│                                              ┌───────────────┐       │
│                                              │OrderDelivery  │       │
│                                              │Worker(@10s)   │       │
│                                              └───────────────┘       │
│                                                     │                │
│                         ┌───────────────────────────┼─────────┐      │
│                         ▼                           ▼         ▼      │
│                   [EXECUTING] ──success──► [COMPLETED]        │      │
│                         │                                     │      │
│                    failure                                    │      │
│                         ▼                                     │      │
│               [FAILED_RETRYING] ──retry──► [EXECUTING]        │      │
│                         │                                     │      │
│                   max retries                                 │      │
│                         ▼                                     │      │
│               [FAILED_PERMANENT] ◄────────────────────────────┘      │
│                    (dead-letter)                                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Health Check Endpoints

### 2.1 Primary Health Check
```bash
curl -s http://localhost:8080/actuator/health | jq .
```

### 2.2 Worker Status
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/worker/status | jq .
```
**Expected response:**
```json
{
  "workerId": "prod-worker-abc123",
  "totalProcessed": 15000,
  "totalCompleted": 14950,
  "totalFailed": 50,
  "totalRetries": 127,
  "recoveredOrphans": 3,
  "isRunning": true
}
```

### 2.3 Invariant Validation
```bash
# Single order
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/invariants/orders/{orderId} | jq .

# Global check (expensive!)
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/invariants/all | jq .

# Orphan check
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/invariants/orphans | jq .
```

### 2.4 Capacity Check
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/capacity | jq .
```

---

## 3. Common Alerts & Responses

### 3.1 ALERT: Dead Letter Queue Spike

**Symptoms:**
- `delivery_dead_letter_queue` > 50
- Multiple orders with `failedPermanentPlays > 0`

**Diagnosis:**
```bash
# Check dead letter queue
curl -s -H "X-Api-Key: $API_KEY" \
  "http://localhost:8080/api/admin/orders/{orderId}/failed-tasks" | jq .

# Check last errors
psql $DATABASE_URL -c "
  SELECT id, last_error, attempts, executed_at
  FROM order_tasks 
  WHERE status = 'FAILED_PERMANENT'
  ORDER BY executed_at DESC
  LIMIT 20;
"
```

**Common Causes:**
1. **Proxy provider outage** - Check BrightData/SOAX dashboards
2. **Rate limiting** - Spotify may be blocking requests
3. **Network issues** - Check connectivity to proxy providers

**Resolution:**
```bash
# If proxy issue, seed more proxies
curl -X POST -H "X-Api-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  http://localhost:8080/api/internal/proxies \
  -d '{"provider": "emergency", "nodeIdentifier": "emergency-1", ...}'

# If Spotify rate limiting, reduce worker batch size
# (requires app restart with env var)
GOODFELLAZ17_WORKER_BATCH_SIZE=5 java -jar app.jar
```

---

### 3.2 ALERT: Orphaned Tasks Accumulating

**Symptoms:**
- `delivery_orphans_recovered_total` spiking
- `delivery_tasks_executing` count stays high
- Worker logs show "Recovering orphaned task"

**Diagnosis:**
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/invariants/orphans | jq .
```

**Common Causes:**
1. **Worker crash/restart** - Normal during deployments
2. **Memory pressure** - Worker may be OOMing
3. **Database connection issues** - Check Neon status

**Resolution:**
- If worker crashed: It will auto-recover on restart. No action needed.
- If memory: `kubectl describe pod` to check memory limits
- If database: Check Neon dashboard for connection pool exhaustion

---

### 3.3 ALERT: Order Stuck in PROCESSING

**Symptoms:**
- Order status is PROCESSING for > 2 hours
- Task progress not advancing

**Diagnosis:**
```bash
# Get order progress
curl -s -H "X-Api-Key: $API_KEY" \
  "http://localhost:8080/api/admin/orders/{orderId}/progress" | jq .

# Check task distribution
psql $DATABASE_URL -c "
  SELECT status, COUNT(*), SUM(quantity) 
  FROM order_tasks 
  WHERE order_id = '{orderId}'
  GROUP BY status;
"
```

**Common Causes:**
1. **All tasks in FAILED_RETRYING** with future retry_after
2. **Worker paused** (chaos injection left on in prod)
3. **No healthy proxies** available

**Resolution:**
```bash
# Force immediate retry (resets retry_after)
psql $DATABASE_URL -c "
  UPDATE order_tasks 
  SET retry_after = NOW() 
  WHERE order_id = '{orderId}' 
    AND status = 'FAILED_RETRYING';
"

# Check worker is not paused
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/testing/status | jq .

# If paused, disable chaos (should not happen in prod!)
curl -X POST -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/admin/testing/disable
```

---

### 3.4 ALERT: Invariant Violation

**Symptoms:**
- `passed: false` from invariant check
- Order shows COMPLETED but delivered ≠ expected

**Diagnosis:**
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  "http://localhost:8080/api/admin/invariants/orders/{orderId}" | jq .
```

**This is a BUG.** Escalate immediately to engineering.

**Collect diagnostic info:**
```bash
# Export order state
psql $DATABASE_URL -c "
  SELECT * FROM orders WHERE id = '{orderId}';
" > /tmp/order-{orderId}.txt

# Export all tasks
psql $DATABASE_URL -c "
  SELECT * FROM order_tasks WHERE order_id = '{orderId}';
" > /tmp/tasks-{orderId}.txt

# Check invariant log
psql $DATABASE_URL -c "
  SELECT * FROM invariant_check_log 
  WHERE order_id = '{orderId}'
  ORDER BY check_timestamp DESC;
"
```

---

## 4. Prometheus Metrics Reference

### 4.1 Key Metrics
```
# Tasks by status (counter)
delivery_tasks_total{status="completed"}
delivery_tasks_total{status="failed_permanent"}

# Current state (gauge)
delivery_tasks_pending
delivery_tasks_executing
delivery_dead_letter_queue
delivery_worker_active

# Operations (counter)
delivery_retries_total
delivery_orphans_recovered_total
delivery_plays_total{outcome="delivered"}
delivery_plays_total{outcome="failed"}

# Latency (histogram)
delivery_task_duration_seconds
delivery_batch_duration_seconds
```

### 4.2 Grafana Dashboard Queries

**Delivery Rate (plays/minute):**
```promql
rate(delivery_plays_total{outcome="delivered"}[5m]) * 60
```

**Failure Rate:**
```promql
rate(delivery_tasks_total{status="failed_permanent"}[5m]) / 
rate(delivery_tasks_total[5m]) * 100
```

**Dead Letter Growth:**
```promql
delta(delivery_dead_letter_queue[1h])
```

---

## 5. Database Queries

### 5.1 Order Health Check
```sql
-- Orders stuck > 2 hours
SELECT id, status, quantity, delivered, created_at,
       NOW() - created_at as age
FROM orders
WHERE status = 'PROCESSING'
  AND created_at < NOW() - INTERVAL '2 hours';
```

### 5.2 Task Distribution
```sql
-- Tasks by status for all active orders
SELECT 
  o.id as order_id,
  o.quantity as order_qty,
  COUNT(*) FILTER (WHERE t.status = 'PENDING') as pending,
  COUNT(*) FILTER (WHERE t.status = 'EXECUTING') as executing,
  COUNT(*) FILTER (WHERE t.status = 'COMPLETED') as completed,
  COUNT(*) FILTER (WHERE t.status = 'FAILED_RETRYING') as retrying,
  COUNT(*) FILTER (WHERE t.status = 'FAILED_PERMANENT') as dead_letter
FROM orders o
JOIN order_tasks t ON t.order_id = o.id
WHERE o.status IN ('PROCESSING', 'PENDING')
GROUP BY o.id, o.quantity;
```

### 5.3 Recent Failures
```sql
-- Last 20 permanent failures with errors
SELECT 
  t.id,
  t.order_id,
  t.quantity,
  t.last_error,
  t.attempts,
  t.executed_at
FROM order_tasks t
WHERE t.status = 'FAILED_PERMANENT'
ORDER BY t.executed_at DESC
LIMIT 20;
```

### 5.4 Invariant Health View
```sql
SELECT * FROM v_invariant_health;
```

---

## 6. Emergency Procedures

### 6.1 Stop All Delivery (Emergency Brake)
```bash
# Option 1: Disable worker via config
kubectl set env deployment/goodfellaz17 GOODFELLAZ17_WORKER_ENABLED=false

# Option 2: Scale to 0
kubectl scale deployment/goodfellaz17 --replicas=0
```

### 6.2 Drain Dead Letter Queue
Only after fixing root cause:
```sql
-- Move failed tasks back to retry queue
UPDATE order_tasks
SET status = 'FAILED_RETRYING',
    attempts = 0,
    retry_after = NOW()
WHERE status = 'FAILED_PERMANENT'
  AND order_id = '{orderId}';
```

### 6.3 Force Order Completion
**LAST RESORT - Only if customer agrees to partial delivery:**
```sql
-- Calculate delivered from completed tasks
UPDATE orders 
SET status = 'COMPLETED',
    delivered = (
      SELECT COALESCE(SUM(quantity), 0) 
      FROM order_tasks 
      WHERE order_id = orders.id AND status = 'COMPLETED'
    ),
    failed_permanent_plays = (
      SELECT COALESCE(SUM(quantity), 0) 
      FROM order_tasks 
      WHERE order_id = orders.id AND status = 'FAILED_PERMANENT'
    )
WHERE id = '{orderId}';
```

---

## 7. Escalation Matrix

| Severity | Condition | Response Time | Contact |
|----------|-----------|---------------|---------|
| P1 | Invariant violation | 15 min | Page on-call |
| P1 | Dead letter > 500 | 15 min | Page on-call |
| P2 | Order stuck > 4h | 1 hour | Slack #15k-alerts |
| P2 | Orphan recovery > 50/hour | 1 hour | Slack #15k-alerts |
| P3 | Single order failure | Next business day | Jira ticket |

---

## 8. Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2025-01-XX | goodfellaz17 | Initial runbook |

