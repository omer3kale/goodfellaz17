# 15K Order Stress Test & Failure Injection Guide

## Overview

This document describes how to stress test the 15k order delivery system with controlled failure injection. The system is designed to guarantee delivery even under:

- Random task execution failures
- Proxy bans and network timeouts
- Application crashes and restarts

## Components

### 1. FailureInjectionService

Located at: `src/main/java/com/goodfellaz17/application/testing/FailureInjectionService.java`

**Only active in `local`, `dev`, `test` profiles.**

Capabilities:
- **Random failures**: Fail X% of task executions
- **Timeout simulation**: Simulate network timeouts
- **Latency injection**: Add artificial delays
- **Proxy banning**: Ban specific proxy IDs
- **Execution pause**: Completely halt processing

### 2. Admin Testing Endpoints

Available only in non-production profiles:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/testing/status` | GET | Get injection status and metrics |
| `/api/admin/testing/enable` | POST | Enable failure injection |
| `/api/admin/testing/disable` | POST | Disable failure injection |
| `/api/admin/testing/reset` | POST | Reset all settings |
| `/api/admin/testing/config` | POST | Configure parameters |
| `/api/admin/testing/pause` | POST | Pause all executions |
| `/api/admin/testing/resume` | POST | Resume executions |
| `/api/admin/testing/ban-proxy/{id}` | POST | Ban a proxy |
| `/api/admin/testing/unban-proxy/{id}` | POST | Unban a proxy |
| `/api/admin/testing/clear-bans` | POST | Clear all bans |
| `/api/admin/testing/preset/mild` | POST | 10% failures |
| `/api/admin/testing/preset/moderate` | POST | 25% failures, 5% timeouts |
| `/api/admin/testing/preset/severe` | POST | 50% failures, 15% timeouts |
| `/api/admin/testing/preset/catastrophic` | POST | 80% failures, 30% timeouts |

### 3. Extended Worker Metrics

The worker status endpoint now includes:

```json
{
  "workerId": "hostname-abc123",
  "isRunning": true,
  "totalProcessed": 150,
  "totalCompleted": 120,
  "totalFailed": 30,
  "transientFailures": 25,
  "permanentFailures": 5,
  "totalRetries": 25,
  "recoveredOrphans": 3,
  "tasksRecoveredAfterStart": 3,
  "activeCount": 2,
  "pendingTasks": 10,
  "executingTasks": 2,
  "workerStartTime": "2024-01-15T10:30:00Z",
  "timestamp": "2024-01-15T10:35:00Z"
}
```

---

## Manual Testing Steps

### Prerequisites

```bash
# Start PostgreSQL (Docker)
docker run -d --name goodfellaz17-pg \
  -e POSTGRES_USER=goodfellaz17 \
  -e POSTGRES_PASSWORD=localdev123 \
  -e POSTGRES_DB=goodfellaz17 \
  -p 5432:5432 \
  postgres:15

# Set Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Start app with local profile
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests
```

### Step 1: Seed Proxies

```bash
# Seed 100 proxies
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
    }"
done

# Verify capacity
curl http://localhost:8080/api/admin/capacity \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

### Step 2: Place 15k Order

```bash
curl -X POST http://localhost:8080/api/v2/orders \
  -H "X-API-Key: test-api-key-local-dev-12345" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: stress-test-$(date +%s)" \
  -d '{
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 15000,
    "targetUrl": "https://open.spotify.com/track/test"
  }' | jq

# Save the order ID
ORDER_ID="<paste-order-id>"
```

### Step 3: Monitor Initial Progress

```bash
# Watch progress
watch -n 2 "curl -s http://localhost:8080/api/admin/orders/$ORDER_ID/progress \
  -H 'X-API-Key: test-api-key-local-dev-12345' | jq '.progressPercent, .completedTasks, .status'"
```

### Step 4: Enable Chaos

```bash
# Enable moderate chaos (25% failures)
curl -X POST http://localhost:8080/api/admin/testing/preset/moderate \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Check injection status
curl http://localhost:8080/api/admin/testing/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

### Step 5: Ban Specific Proxies

```bash
# Get proxy list
curl http://localhost:8080/api/internal/proxies \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.[0:3] | .[].id'

# Ban first proxy for 60 seconds
PROXY_ID="<proxy-id>"
curl -X POST "http://localhost:8080/api/admin/testing/ban-proxy/$PROXY_ID?durationSeconds=60" \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

### Step 6: Simulate App Crash

```bash
# Record current progress
curl http://localhost:8080/api/admin/orders/$ORDER_ID/progress \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Kill the app (harsh crash)
lsof -ti :8080 | xargs kill -9

# Wait 5 seconds
sleep 5

# Restart the app
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests &

# Wait for startup
sleep 15

# Check recovery metrics
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.tasksRecoveredAfterStart, .recoveredOrphans'
```

### Step 7: Disable Chaos & Complete

```bash
# Disable chaos
curl -X POST http://localhost:8080/api/admin/testing/disable \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Wait for completion (monitor)
while true; do
  status=$(curl -s http://localhost:8080/api/admin/orders/$ORDER_ID/progress \
    -H "X-API-Key: test-api-key-local-dev-12345" | jq -r '.status')
  echo "Status: $status"
  [ "$status" == "COMPLETED" ] && break
  sleep 5
done
```

### Step 8: Validate Results

```bash
# Final progress
curl http://localhost:8080/api/admin/orders/$ORDER_ID/progress \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Check dead letter queue
curl http://localhost:8080/api/admin/orders/$ORDER_ID/failed-tasks \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq

# Worker final metrics
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq
```

---

## Automated Torture Test

Run the automated torture test script:

```bash
chmod +x scripts/15k-torture-test.sh
./scripts/15k-torture-test.sh
```

This script:
1. Seeds 100 proxies
2. Places a 15k order
3. Monitors initial progress
4. Enables moderate chaos (25% failures)
5. Bans 3 proxies temporarily
6. Monitors under chaos conditions
7. Kills and restarts the app
8. Verifies recovery
9. Disables chaos
10. Waits for completion
11. Validates final state

---

## Validation Checklist

After running the torture test, verify:

- [ ] Order status is `COMPLETED`
- [ ] `delivered + failedPermanent = quantity` (all plays accounted for)
- [ ] Dead letter queue only contains truly unrecoverable failures
- [ ] No double-counting (delivered matches sum of completed task quantities)
- [ ] Orphan recovery worked (check `recoveredOrphans` metric)
- [ ] Retries happened (check `totalRetries` metric)

---

## Chaos Presets Reference

| Preset | Failure % | Timeout % | Latency |
|--------|-----------|-----------|---------|
| mild | 10% | 0% | 0ms |
| moderate | 25% | 5% | 100ms |
| severe | 50% | 15% | 500ms |
| catastrophic | 80% | 30% | 2000ms |

---

## Troubleshooting

### Worker Not Processing Tasks

```bash
# Check if worker is enabled
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.isRunning'

# Check if injection is paused
curl http://localhost:8080/api/admin/testing/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.injectionStatus.paused'
```

### Tasks Stuck in EXECUTING

Tasks are considered orphaned after 120 seconds. The worker will automatically recover them. If you need to force recovery:

```bash
# Check executing tasks
curl http://localhost:8080/api/admin/worker/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.executingTasks'
```

### All Tasks Failing

Check if injection is too aggressive:

```bash
curl http://localhost:8080/api/admin/testing/status \
  -H "X-API-Key: test-api-key-local-dev-12345" | jq '.injectionStatus.failurePercentage'

# Lower the failure rate
curl -X POST http://localhost:8080/api/admin/testing/config \
  -H "X-API-Key: test-api-key-local-dev-12345" \
  -H "Content-Type: application/json" \
  -d '{"failurePercentage": 10}' | jq
```

---

## Security Notes

- FailureInjectionService is **completely disabled in production**
- The `@Profile({"local", "dev", "test"})` annotation ensures endpoints don't exist in prod
- `NoOpFailureInjectionService` is loaded in production as a safety fallback
- All injection attempts in production are logged and blocked
