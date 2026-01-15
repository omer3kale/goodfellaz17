# 15K Order Delivery System - Test Plan

## System Overview

The 15k delivery guarantee system provides bulletproof execution for large orders (>1000 plays):

- **Task-based execution**: Orders split into 200-500 play tasks
- **Strict idempotency**: Each task has a unique idempotency token
- **Recovery**: Worker picks up orphaned tasks on restart
- **Dead-letter queue**: Permanently failed tasks tracked separately
- **Progress visibility**: Admin endpoints for monitoring

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          ORDER FLOW (15k)                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. Customer submits 15,000 play order via /api/v1/orders               │
│                              ↓                                           │
│  2. OrderExecutionService detects 15k, checks capacity                   │
│                              ↓                                           │
│  3. Creates OrderEntity with status=RUNNING, usesTaskDelivery=true      │
│                              ↓                                           │
│  4. TaskGenerationService generates ~38 tasks (400 plays each)          │
│     - Tasks spread across 48-72h window                                  │
│     - Each task has scheduled_at time                                    │
│                              ↓                                           │
│  5. OrderDeliveryWorker runs @Scheduled(10 seconds)                      │
│     - Picks up PENDING tasks where scheduled_at <= now                   │
│     - Claims task (EXECUTING status)                                     │
│     - Executes delivery via proxy                                        │
│     - Completes task or retries (max 3 attempts)                         │
│                              ↓                                           │
│  6. When all tasks complete, order status → COMPLETED                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Test Environment Setup

### Prerequisites

```bash
# Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# PostgreSQL running locally (or use Docker)
docker run -d --name goodfellaz17-pg \
  -e POSTGRES_USER=goodfellaz17 \
  -e POSTGRES_PASSWORD=localdev123 \
  -e POSTGRES_DB=goodfellaz17 \
  -p 5432:5432 \
  postgres:15
```

### Application Configuration

```yaml
# application-local.yml (already configured)
goodfellaz17:
  task:
    time-multiplier: 720  # 72 hours → 6 minutes for testing
  worker:
    interval-ms: 10000    # Check every 10 seconds
    batch-size: 5         # Process 5 tasks per tick
```

### Start Application

```bash
cd /Users/omer3kale/Desktop/goodfellaz17
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Test Case 1: Proxy Seeding

Before testing 15k orders, ensure enough proxy capacity exists.

### Seed Proxies Script

```bash
# Seed 100 datacenter proxies (200 plays/hour each = 20,000/hr capacity)
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/internal/proxies \
    -H "X-API-Key: test-api-key-local-dev-12345" \
    -H "Content-Type: application/json" \
    -d "{
      \"proxyUrl\": \"http://proxy-dc-${i}.local:8080\",
      \"tier\": \"DATACENTER\",
      \"label\": \"datacenter-${i}\",
      \"maxPlaysPerHour\": 200,
      \"maxConcurrent\": 10
    }" &
done
wait
echo "✅ Seeded 100 proxies"
```

### Verify Proxy Count

```bash
curl http://localhost:8080/api/admin/capacity \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

Expected output:
```json
{
  "playsPerHour": 20000,
  "max48hCapacity": 960000,
  "max72hCapacity": 1440000,
  "healthyProxyCount": 100,
  "canAccept15k": true
}
```

---

## Test Case 2: Place 15k Order

### Create Order

```bash
SERVICE_ID="3c1cb593-85a7-4375-8092-d39c00399a7b"

curl -X POST http://localhost:8080/api/v1/orders \
  -H "X-API-Key: test-api-key-local-dev-12345" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-15k-order-001" \
  -d "{
    \"serviceId\": \"${SERVICE_ID}\",
    \"link\": \"https://open.spotify.com/track/test15k\",
    \"quantity\": 15000
  }" | jq
```

Expected response:
```json
{
  "orderId": "uuid-here",
  "status": "RUNNING",
  "quantity": 15000,
  "delivered": 0,
  "estimatedCompletionAt": "2024-XX-XXTXX:XX:XX",
  "message": "Order accepted for task-based delivery"
}
```

### Save Order ID

```bash
ORDER_ID="<paste-order-id-here>"
```

---

## Test Case 3: Verify Task Generation

### Check Order Tasks

```bash
curl "http://localhost:8080/api/admin/orders/${ORDER_ID}/tasks" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

Expected:
```json
{
  "orderId": "uuid",
  "totalQuantity": 15000,
  "delivered": 0,
  "status": "RUNNING",
  "taskCount": 38,
  "tasks": [
    {
      "id": "task-uuid-1",
      "sequenceNumber": 1,
      "quantity": 400,
      "status": "PENDING",
      "attempts": 0,
      "scheduledAt": "2024-..."
    },
    // ... 37 more tasks
  ]
}
```

### Verify Task Distribution

- ~38 tasks for 15,000 plays (400 plays/task)
- Tasks scheduled across 6 minutes (with 720x multiplier)
- ~10 seconds between tasks

---

## Test Case 4: Monitor Worker Execution

### Watch Worker Status

```bash
# Poll every 5 seconds
while true; do
  clear
  echo "=== Worker Status $(date) ==="
  curl -s "http://localhost:8080/api/admin/worker/status" \
    -H "X-API-Key: test-api-key-local-dev-12345" | jq
  
  echo "\n=== Order Progress ==="
  curl -s "http://localhost:8080/api/admin/orders/${ORDER_ID}/progress" \
    -H "X-API-Key: test-api-key-local-dev-12345" | jq '.progressPercent, .completedTasks, .totalTasks, .status'
  
  sleep 5
done
```

Expected flow:
1. Worker picks up tasks as they become scheduled
2. `completedTasks` increases incrementally
3. `progressPercent` grows toward 100%
4. `delivered` count matches completed task quantities

---

## Test Case 5: Observe Order Completion

### Wait for Completion

With 720x multiplier, 15k order completes in ~6 minutes.

```bash
# Check final state
curl "http://localhost:8080/api/admin/orders/${ORDER_ID}/progress" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

Expected final state:
```json
{
  "orderId": "uuid",
  "totalQuantity": 15000,
  "delivered": 15000,
  "remains": 0,
  "failedPermanent": 0,
  "progressPercent": 100.0,
  "status": "COMPLETED",
  "totalTasks": 38,
  "completedTasks": 38,
  "failedTasks": 0,
  "onSchedule": true
}
```

---

## Test Case 6: Simulate Proxy Failure

### Force Proxy Failures

To test retry logic, you can:

1. **Delete proxies mid-order**:
```bash
# Get proxy IDs and delete some
curl http://localhost:8080/api/internal/proxies \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.[0].id'

PROXY_ID="<proxy-id>"
curl -X DELETE "http://localhost:8080/api/internal/proxies/${PROXY_ID}" \
  -H "X-API-Key: test-api-key-local-dev-12345"
```

2. **Set proxy to unhealthy**:
```bash
curl -X POST "http://localhost:8080/api/internal/proxies/${PROXY_ID}/health" \
  -H "X-API-Key: test-api-key-local-dev-12345" \
  -H "Content-Type: application/json" \
  -d '{"healthy": false}'
```

### Monitor Retries

```bash
# Check for failing tasks
curl "http://localhost:8080/api/admin/orders/${ORDER_ID}/tasks" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.tasks[] | select(.status == "FAILED_RETRYING")'
```

Expected behavior:
- Task retries up to 3 times
- Exponential backoff: 30s → 60s → 120s
- After 3 failures: FAILED_PERMANENT

---

## Test Case 7: Dead Letter Queue

### View Failed Tasks

```bash
# Order-specific failures
curl "http://localhost:8080/api/admin/orders/${ORDER_ID}/failed-tasks" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Global dead letter queue
curl "http://localhost:8080/api/admin/dead-letter-queue?limit=50" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

Expected:
```json
{
  "orderId": "uuid",
  "failedPlays": 400,
  "failedTaskCount": 1,
  "failedTasks": [
    {
      "taskId": "uuid",
      "sequenceNumber": 15,
      "quantity": 400,
      "attempts": 3,
      "lastError": "All proxies exhausted",
      "lastProxyNodeId": "proxy-uuid"
    }
  ]
}
```

---

## Test Case 8: Crash Recovery

### Simulate App Crash

1. Start a 15k order
2. Wait for ~10 tasks to execute
3. Kill the app (Ctrl+C or kill -9)
4. Restart the app

### Verify Recovery

```bash
# After restart, check order progress
curl "http://localhost:8080/api/admin/orders/${ORDER_ID}/progress" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

Expected:
- Progress preserved (completed tasks remain COMPLETED)
- EXECUTING tasks detected as orphaned (>120s)
- Worker resumes from where it left off
- Order eventually completes

---

## Test Case 9: Idempotency Check

### Replay Same Order

```bash
# Submit same idempotency key again
curl -X POST http://localhost:8080/api/v1/orders \
  -H "X-API-Key: test-api-key-local-dev-12345" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-15k-order-001" \
  -d "{
    \"serviceId\": \"${SERVICE_ID}\",
    \"link\": \"https://open.spotify.com/track/test15k\",
    \"quantity\": 15000
  }" | jq
```

Expected:
- Returns same order (no new order created)
- `orderId` matches original
- Status reflects current state (RUNNING or COMPLETED)

---

## Performance Expectations

| Metric | Expected Value |
|--------|----------------|
| Task generation | <500ms for 15k order |
| Worker throughput | 5 tasks/tick × 6 ticks/min = 30 tasks/min |
| 15k completion (dev) | ~6 minutes (with 720x multiplier) |
| 15k completion (prod) | 48-72 hours |
| Max concurrent tasks | 5 per worker tick |

---

## Troubleshooting

### Worker Not Processing

Check if worker is enabled:
```bash
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.isRunning'
```

Check for pending tasks:
```bash
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.pendingTasks'
```

### Tasks Stuck in EXECUTING

Check orphan threshold (default 120s):
```bash
# Tasks orphaned after 2 minutes of no response
# Worker will reset and retry these
```

### No Proxies Available

```bash
curl http://localhost:8080/api/admin/capacity \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.healthyProxyCount'
```

---

## Cleanup Script

```bash
# Reset database (use with caution!)
docker exec -it goodfellaz17-pg psql -U goodfellaz17 -d goodfellaz17 -c "
  DELETE FROM order_tasks;
  DELETE FROM orders;
  DELETE FROM proxy_nodes;
"
```

---

## Summary

This test plan covers:

1. ✅ Proxy seeding for capacity
2. ✅ 15k order placement
3. ✅ Task generation verification
4. ✅ Worker execution monitoring
5. ✅ Order completion verification
6. ✅ Proxy failure simulation
7. ✅ Dead letter queue inspection
8. ✅ Crash recovery testing
9. ✅ Idempotency verification

Run through these test cases to validate the 15k delivery guarantee system is bulletproof.
