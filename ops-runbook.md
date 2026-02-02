# GoodfellaZ17 Ops Runbook - 15k Order Delivery System

**Last Updated:** 2025-02-02
**Audience:** On-call engineers, DevOps, SREs
**Criticality:** HIGH (revenue-blocking service)

---

## 1. Quick Health Check (5 min - do this first)

### 1.1 Application Health

```bash
# Is the app running?
curl -s http://localhost:8080/actuator/health | jq '.status'
# Expected: "UP"

# Are workers running?
curl -s http://localhost:8080/api/admin/worker-status | jq '.workerStatus'
# Expected: "RUNNING" with lastTaskTime < 10s ago
```

### 1.2 Database Connection

```bash
# Can we reach PostgreSQL?
psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 -c "SELECT 1"
# Expected: Single row with value 1

# Recent orders?
psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 \
  -c "SELECT COUNT(*), status FROM orders WHERE created_at > NOW() - INTERVAL '1 hour' GROUP BY status"
# Expected: Multiple rows, COMPLETED count > PROCESSING count
```

### 1.3 Critical Metrics

```bash
# Orders completed vs failed (past hour)
curl -s http://localhost:8080/actuator/metrics/orders.completed \
  | jq '.measurements[0].value'

curl -s http://localhost:8080/actuator/metrics/orders.failed \
  | jq '.measurements[0].value'

# Proxy health
curl -s http://localhost:8080/actuator/metrics/proxy.healthy \
  | jq '.measurements[0].value'
# If < 20: **URGENT** - Proxies are down, orders will fail

# Account health
psql ... -c "SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'"
# If < 300: **URGENT** - Account pool depleted, orders will fail
```

---

## 2. Red Alert Scenarios

### 2.1 🔴 Proxy Pool Degraded (< 20 healthy proxies)

**Symptoms:**
- `proxy.healthy < 20` (Prometheus alert fires)
- Worker logs show proxy ban events spiking
- Orders stuck in PROCESSING state

**Root Causes:**
1. Datacenter IP blocked by Spotify (temporal)
2. Proxy vendor returned invalid proxies
3. Proxy rotation config is too strict

**Recovery Steps:**

```bash
# Step 1: Check current proxy count
curl http://localhost:8080/actuator/metrics/proxy.healthy | jq '.measurements[0].value'

# Step 2: Refresh proxy pool (rotates all IPs)
curl -X POST http://localhost:8080/api/admin/proxies/refresh

# Step 3: Wait 2 minutes for new proxies to load
sleep 120

# Step 4: Verify recovery
curl http://localhost:8080/actuator/metrics/proxy.healthy | jq '.measurements[0].value'
# Expected: >= 20

# Step 5: If still low, check proxy vendor status page
# - luminati.io: Check account credit balance
# - smartproxy.com: Check IP pool freshness
```

**If proxy vendor is down:**

```bash
# Temporary: Use local proxy fallback (if configured)
curl -X POST http://localhost:8080/api/admin/proxies/use-fallback

# Permanent: Switch proxy vendor in application-prod.yml
kubectl edit configmap goodfellaz17-proxy-config
# Change: proxy.vendor: luminati → smartproxy
kubectl rollout restart deployment goodfellaz17
```

---

### 2.2 🔴 Account Pool Depleted (< 300 healthy accounts)

**Symptoms:**
- Database query returns account count < 300
- Worker logs: "INSUFFICIENT_ACCOUNTS" errors
- Orders fail with "No available accounts" message

**Root Causes:**
1. Account ban wave from Spotify (security)
2. Account provisioning script crashed
3. Email provider (GMX) blocked bot traffic

**Recovery Steps:**

```bash
# Step 1: Check account health
psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 \
  -c "SELECT status, COUNT(*) FROM accounts WHERE created_at > NOW() - INTERVAL '7 days' GROUP BY status"

# Sample output:
# ACTIVE | 280
# BANNED | 45
# DEGRADED | 12

# Step 2: If BANNED count is high (>20), Spotify is banning
psql ... -c "SELECT id, email, banned_at FROM accounts WHERE status='BANNED' ORDER BY banned_at DESC LIMIT 5"

# Step 3: Stop accepting new 15k orders (circuit breaker)
curl -X POST http://localhost:8080/api/admin/circuit-breaker/open \
  -H "Content-Type: application/json" \
  -d '{"reason": "Account pool depleted, waiting for provisioning"}'

# Step 4: Trigger emergency account provisioning
curl -X POST http://localhost:8080/api/admin/accounts/provision-emergency \
  -H "Content-Type: application/json" \
  -d '{"target_count": 500, "speed": "maximum"}'
# This will run GMX + Spotify provisioning scripts at max speed
# Expect: 100 new accounts per hour

# Step 5: Monitor account growth
watch -n 30 'psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 \
  -c "SELECT COUNT(*) as active_accounts FROM accounts WHERE status='\''ACTIVE'\''"'

# Step 6: Once accounts > 300, re-open circuit breaker
curl -X POST http://localhost:8080/api/admin/circuit-breaker/close
```

---

### 2.3 🔴 Orders Stuck in PROCESSING (> 1000 for > 30 min)

**Symptoms:**
- Prometheus alert: `orders.pending > 1000`
- `/api/admin/order-progress` shows recent orders still PROCESSING
- Customer complaints: "Still waiting after 2 hours"

**Root Causes:**
1. Worker thread crashed or hung
2. Database connection pool exhausted
3. Disk space on PostgreSQL server full

**Recovery Steps:**

```bash
# Step 1: Check worker status
curl http://localhost:8080/api/admin/worker-status

# Sample response:
# {
#   "workerStatus": "RUNNING",
#   "lastTaskTime": "2025-02-02T10:15:23Z",
#   "tasksProcessed": 1250,
#   "activeTaskCount": 5
# }

# If lastTaskTime > 5 minutes ago: **Worker is hung**

# Step 2: If worker is hung, restart the pod
kubectl delete pod -n production goodfellaz17-0
# Pod will restart with fresh state

# Step 3: If worker is running but orders are stuck:
# Check database connections
psql ... -c "SELECT count(*) FROM pg_stat_activity WHERE datname='goodfellaz17'"
# If > 20: Connection pool exhausted

# Step 4: Increase R2DBC pool size
kubectl set env deployment/goodfellaz17 \
  SPRING_R2DBC_POOL_MAX_SIZE=30
kubectl rollout restart deployment/goodfellaz17

# Step 5: Monitor order completion
curl http://localhost:8080/api/admin/order-progress | jq '.ordersStuck'
# Expected: decreases as worker catches up
```

---

### 2.4 🔴 High Failure Rate (> 5% failed orders)

**Symptoms:**
- `orders.failed` counter increasing rapidly
- Prometheus alert: `orders.success_rate < 95%`
- Dead-letter queue growing

**Root Causes:**
1. Spotify API changes (unexpected response format)
2. Proxy quality degraded (too many blocks)
3. Account cookies expiring (session invalid)

**Recovery Steps:**

```bash
# Step 1: Check failure reasons
psql ... \
  -c "SELECT error_message, COUNT(*) FROM orders WHERE status='FAILED_PERMANENT' AND created_at > NOW() - INTERVAL '1 hour' GROUP BY error_message"

# Common error patterns:
# - "Unauthorized (401)": Spotify auth changed → need to update login logic
# - "Rate Limited (429)": Too many requests → reduce concurrency
# - "Proxy blocked (403)": Proxies IP-banned → refresh proxy pool (see 2.1)

# Step 2: If error is "Unauthorized (401)":
# This indicates Spotify changed their login flow
# Action: Page the backend team to review GoodfellaZ17IntegrationOrchestrator.java

# Step 3: If error is "Rate Limited (429)":
# Reduce concurrent orders
kubectl set env deployment/goodfellaz17 \
  APP_SAFETY_MAX_CONCURRENT_ORDERS=5
# (Default is 15)

# Step 4: Monitor failure rate recovery
watch -n 30 'curl -s http://localhost:8080/api/metrics/orders.success-rate | grep -oP "\d+\.\d+"'
# Expected: Increases back to > 95% within 5 minutes
```

---

### 2.5 🔴 Worker Crashes / High Memory

**Symptoms:**
- Pod OOMKilled (Out of Memory)
- Worker logs: "java.lang.OutOfMemoryError"
- Orders stop processing entirely

**Root Causes:**
1. Task batch too large (memory leak in task processing)
2. Metrics/JVM garbage collection not keeping up
3. Order task queue growing unbounded

**Recovery Steps:**

```bash
# Step 1: Check pod memory
kubectl top pod -n production goodfellaz17-0

# Step 2: Increase memory limit
kubectl patch deployment goodfellaz17 -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"goodfellaz17","resources":{"limits":{"memory":"2Gi"}}}]}}}}'

# Default is 1Gi, increase to 2Gi or 4Gi if needed

# Step 3: Reduce task batch size to free memory
kubectl set env deployment/goodfellaz17 \
  WORKER_BATCH_SIZE=3
# (Default is 5, reduces memory per worker cycle)

# Step 4: Restart pod with new limits
kubectl rollout restart deployment/goodfellaz17

# Step 5: Monitor memory usage
watch -n 10 'kubectl top pod -n production goodfellaz17-0'
# Expected: Stabilizes below 70% limit
```

---

## 3. Dead-Letter Queue Inspection

When orders fail permanently, they move to dead-letter (DLQ) for manual review.

### 3.1 Find failed orders

```bash
psql ... \
  -c "SELECT id, service_id, quantity, error_message, created_at FROM orders WHERE status='FAILED_PERMANENT' ORDER BY created_at DESC LIMIT 10"

# Or via REST API:
curl http://localhost:8080/api/admin/failed-tasks?limit=20

# Response:
# {
#   "failedTasks": [
#     {
#       "orderId": "uuid-123",
#       "taskId": "uuid-456",
#       "quantity": 500,
#       "error": "Proxy blocked (403 Forbidden)",
#       "failedAt": "2025-02-02T10:22:15Z"
#     }
#   ]
# }
```

### 3.2 Retry a failed order

```bash
# Option 1: Retry entire order
curl -X POST http://localhost:8080/api/admin/orders/uuid-123/retry

# Option 2: Retry specific task
curl -X POST http://localhost:8080/api/admin/tasks/uuid-456/retry

# This resets task status to PENDING, worker will retry on next cycle
```

### 3.3 Mark order as lost (don't retry)

```bash
# If error is unrecoverable (e.g., Spotify changed API):
curl -X POST http://localhost:8080/api/admin/orders/uuid-123/cancel \
  -d '{"reason": "Spotify API changed, manual fix required"}'

# Customer gets refund, order removed from active queue
```

---

## 4. Scaling Decisions

### 4.1 Scale up for 15k+ orders

```bash
# Current: 1 worker processing max 5 concurrent tasks

# If queue > 100 pending orders:
kubectl scale deployment goodfellaz17 --replicas=3

# If queue > 500:
kubectl autoscale deployment goodfellaz17 \
  --min=2 --max=5 \
  --cpu-percent=70

# Monitor scaling
watch -n 5 'kubectl get hpa'
```

### 4.2 Database connection scaling

```bash
# Monitor connections
psql ... -c "SELECT count(*) FROM pg_stat_activity WHERE datname='goodfellaz17'"

# If approaching max (20):
kubectl set env deployment/goodfellaz17 \
  SPRING_R2DBC_POOL_MAX_SIZE=30

# Then restart
kubectl rollout restart deployment/goodfellaz17
```

---

## 5. Monitoring Dashboard (Prometheus)

### 5.1 Critical metrics to watch

```
# Success rate (should be > 95%)
rate(orders.completed[5m]) / rate(orders.total[5m]) * 100

# Pending orders (should decrease)
SELECT count(*) FROM orders WHERE status='PROCESSING'

# Orphans recovered (should be < 5% of completed)
rate(tasks.orphans.recovered[1h]) / rate(tasks.completed[1h])

# Proxy availability
proxy.healthy / proxy.total * 100

# Account availability
SELECT count(*) FROM accounts WHERE status='ACTIVE' / 1000 * 100
```

### 5.2 PagerDuty integration

These metrics trigger immediate alerts:

```
proxy.healthy < 20              → Page oncall
accounts.active < 300           → Page oncall
orders.failed.rate > 5%         → Page oncall
orders.pending > 1000           → Page oncall
worker.status == 0              → Page oncall
```

---

## 6. Emergency Procedures

### 6.1 Pause all 15k orders (circuit breaker open)

```bash
curl -X POST http://localhost:8080/api/admin/circuit-breaker/open \
  -d '{"reason": "Spotify service unavailable, pausing orders"}'

# Existing workers finish current tasks
# New orders are rejected with 503

# Resume when fixed:
curl -X POST http://localhost:8080/api/admin/circuit-breaker/close
```

### 6.2 Full rollback (emergency)

```bash
# Revert to previous version
kubectl rollout undo deployment/goodfellaz17

# Verify old version is running
kubectl get deployment goodfellaz17 -o jsonpath='{.spec.template.spec.containers[0].image}'

# Check if orders resume
curl http://localhost:8080/api/admin/worker-status | jq '.workerStatus'
```

### 6.3 Disable failure injection (if chaos enabled)

```bash
# In test/dev profiles, chaos testing might be enabled
curl -X POST http://localhost:8080/api/admin/chaos/disable

# Production profile has it disabled by default
# Verify: curl http://localhost:8080/api/admin/chaos/status
# Should return: {"failureInjectionEnabled": false}
```

---

## 7. Performance Tuning

### 7.1 Worker batch size vs latency trade-off

```bash
# Default: 5 concurrent tasks per worker

# For better latency (faster order completion):
WORKER_BATCH_SIZE=3

# For higher throughput (more orders per minute):
WORKER_BATCH_SIZE=10
```

### 7.2 Task split size

```bash
# Default: 500 plays per task

# For faster recovery (smaller tasks):
TASK_SPLIT_SIZE=250

# For fewer database round-trips:
TASK_SPLIT_SIZE=1000
```

---

## 8. Log Files & Debugging

### 8.1 Check worker logs

```bash
# Tail live logs
kubectl logs -f deployment/goodfellaz17 | grep "OrderDeliveryWorker"

# Recent errors
kubectl logs deployment/goodfellaz17 | grep "ERROR" | tail -20

# Search for specific task
kubectl logs deployment/goodfellaz17 | grep "taskId=abc-123"
```

### 8.2 Database audit query

```bash
# Orders created today
psql ... -c "SELECT COUNT(*) FROM orders WHERE created_at::date = CURRENT_DATE"

# Orders completed today
psql ... -c "SELECT COUNT(*) FROM orders WHERE status='COMPLETED' AND completed_at::date = CURRENT_DATE"

# Average delivery time
psql ... -c "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - created_at))) FROM orders WHERE status='COMPLETED' AND created_at > NOW() - INTERVAL '24 hours'"
# Result is in seconds
```

---

## 9. Contact & Escalation

**On-Call Engineer:** Check PagerDuty schedule
**Backend Team Lead:** @backend-oncall
**Infrastructure Team:** #infrastructure-alerts (Slack)

---

## Appendix A: Environment Variables Reference

| Variable | Default | Prod | Note |
|----------|---------|------|------|
| `SPRING_PROFILES_ACTIVE` | dev | prod | Production hardens all guards |
| `WORKER_BATCH_SIZE` | 5 | 5 | Tasks processed per cycle |
| `APP_SAFETY_MAX_CONCURRENT_ORDERS` | 100 | 15 | Circuit breaker threshold |
| `SPRING_R2DBC_POOL_MAX_SIZE` | 20 | 20 | Database connection pool |
| `APP_TIME_MULTIPLIER` | 720 | 1 | Time compression (test only) |
| `APP_FAILURE_INJECTION_ENABLED` | true | false | Chaos testing (disabled in prod) |

---

## Appendix B: Useful Aliases

Add to `.bashrc` or `.zshrc`:

```bash
alias 15k-health="curl -s http://localhost:8080/api/admin/worker-status | jq"
alias 15k-metrics="curl -s http://localhost:8080/actuator/metrics/orders.completed | jq"
alias 15k-db-count="psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 -c \"SELECT COUNT(*), status FROM orders WHERE created_at > NOW() - INTERVAL '1 hour' GROUP BY status\""
alias 15k-failed="psql -h $NEON_HOST -U $NEON_USER -d goodfellaz17 -c \"SELECT COUNT(*) FROM orders WHERE status='FAILED_PERMANENT' AND created_at > NOW() - INTERVAL '1 hour'\""
alias 15k-restart="kubectl rollout restart deployment/goodfellaz17 && kubectl get pods -w"
```

---

**Last Review:** 2025-02-02
**Next Review:** 2025-03-02
**Maintained By:** DevOps Team
