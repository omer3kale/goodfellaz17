# 1k Order Flow Test Plan

## Overview

This test plan validates the production-ready 1k order path with:
- **Atomic transactions** (balance + order + ledger)
- **Idempotency** (safe retries via `idempotencyKey`)
- **Input validation** (quantity, URL, service)
- **Instant execution** (≤1k orders auto-complete in local/dev)

---

## Prerequisites

1. **Docker running with Postgres**:

   ```bash
   docker-compose up -d postgres
   ```

2. **App running with local profile**:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests
   ```

3. **Test data seeded** (LocalDevSeeder):
   - API Key: `test-api-key-local-dev-12345`
   - User Email: `localdev@goodfellaz17.test`
   - Service ID: `3c1cb593-85a7-4375-8092-d39c00399a7b`
   - Initial Balance: $1000.00

---

## Test Cases

### Test 1: Basic 1k Order (Instant Execution in local)

**Request:**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 1000,
    "targetUrl": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh"
  }' | jq .
```

**Expected Response (201 Created):**

```json
{
  "id": "uuid-here",
  "quantity": 1000,
  "delivered": 1000,           // Instant delivery in local profile!
  "status": "COMPLETED",       // COMPLETED (not PENDING) in local
  "estimatedCompletionAt": "...",
  "estimatedHours": 0,
  "wasIdempotentRetry": false
}
```

**Expected DB State:**

```sql
SELECT id, quantity, delivered, remains, status, total_cost
FROM orders WHERE quantity = 1000 ORDER BY created_at DESC LIMIT 1;
```

| id | quantity | delivered | remains | status | total_cost |
|----|----------|-----------|---------|--------|------------|
| uuid | 1000 | 1000 | 0 | COMPLETED | 2.00 |

---

### Test 2: Idempotency - First Request

**Request:**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 500,
    "targetUrl": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh",
    "idempotencyKey": "my-panel-order-12345"
  }' | jq .
```

**Expected Response (201 Created):**

```json
{
  "id": "uuid-A",
  "quantity": 500,
  "delivered": 500,
  "status": "COMPLETED",
  "wasIdempotentRetry": false
}
```

Save the returned `id` as ORDER_ID for next test.

---

### Test 3: Idempotency - Retry Same Key (No Double Charge!)

**Request (same `idempotencyKey`):**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 500,
    "targetUrl": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh",
    "idempotencyKey": "my-panel-order-12345"
  }' | jq .
```

**Expected Response (200 OK - not 201!):**

```json
{
  "id": "uuid-A",              // SAME ID as before
  "quantity": 500,
  "delivered": 500,
  "status": "COMPLETED",
  "wasIdempotentRetry": true   // Indicates this was a retry
}
```

**Verify No Double Charge:**

```sql
SELECT COUNT(*) FROM orders WHERE external_order_id = 'my-panel-order-12345';
-- Should return: 1 (not 2!)

SELECT balance FROM users WHERE api_key = 'test-api-key-local-dev-12345';
-- Balance should be reduced ONCE, not twice
```

---

### Test 4: Validation - Invalid URL

**Request:**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 1000,
    "targetUrl": "https://youtube.com/watch?v=123"
  }' | jq .
```

**Expected Response (422 Unprocessable Entity):**

```json
{
  "code": "url_invalid",
  "message": "URL must be a valid Spotify track, album, playlist, or artist URL",
  "details": null
}
```

---

### Test 5: Validation - Quantity Too Low

**Request:**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 50,
    "targetUrl": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh"
  }' | jq .
```

**Expected Response (422 Unprocessable Entity):**

```json
{
  "code": "quantity_too_low",
  "message": "Minimum quantity is 100 for Spotify Track Plays",
  "details": null
}
```

---

### Test 6: Insufficient Balance

**Drain the balance first, then request large order:**

```bash
curl -s -X POST http://localhost:8080/api/public/orders \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "test-api-key-local-dev-12345",
    "serviceId": "3c1cb593-85a7-4375-8092-d39c00399a7b",
    "quantity": 100000,
    "targetUrl": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh"
  }' | jq .
```

**Expected Response (402 Payment Required):**

```json
{
  "code": "balance_insufficient",
  "message": "Need €XXX.XX more. Required: €200.00, Available: €YYY.YY",
  "details": null
}
```

---

### Test 7: Order Status Check

**Request:**

```bash
ORDER_ID="<uuid-from-test-2>"
curl -s "http://localhost:8080/api/public/orders/$ORDER_ID?apiKey=test-api-key-local-dev-12345" | jq .
```

**Expected Response (200 OK):**

```json
{
  "id": "uuid-A",
  "quantity": 500,
  "delivered": 500,
  "remains": 0,
  "status": "COMPLETED",
  "progressPercent": 100.0,
  "estimatedCompletionAt": "...",
  "createdAt": "...",
  "startedAt": "...",
  "completedAt": "..."
}
```

---

### Test 8: Capacity Check

**Request:**

```bash
curl -s http://localhost:8080/api/public/capacity/summary | jq .
```

**Expected Response (200 OK):**

```json
{
  "canAccept15kOrder": true,
  "max72hCapacity": 20448,
  "available72h": 20448,
  "etaHintHours": 52.5,
  "slotsFor15k": 1,
  "playsPerHour": 284,
  "message": "Ready to accept orders. 1 slots available for 15k packages.",
  "calculatedAt": "..."
}
```

---

## DB Verification Queries

### Check Order + Transaction Atomicity

```sql
-- Orders and transactions should match
SELECT
    o.id as order_id,
    o.quantity,
    o.total_cost,
    o.status,
    bt.amount as tx_amount,
    bt.balance_before,
    bt.balance_after
FROM orders o
JOIN balance_transactions bt ON bt.order_id = o.id
ORDER BY o.created_at DESC
LIMIT 5;
```

### Check No Duplicate Orders for Same Idempotency Key

```sql
SELECT external_order_id, COUNT(*)
FROM orders
WHERE external_order_id IS NOT NULL
GROUP BY external_order_id
HAVING COUNT(*) > 1;
-- Should return EMPTY (no duplicates)
```

### Check User Balance After Tests

```sql
SELECT email, balance, api_key
FROM users
WHERE api_key = 'test-api-key-local-dev-12345';
```

---

## Logging Verification

During tests, check the app logs for structured logging:

```bash
# Watch for order creation logs
grep "ORDER_" /path/to/app.log | tail -20
```

**Expected log patterns:**

```
ORDER_CREATE_START | userId=uuid | quantity=1000 | serviceId=uuid | idempotencyKey=none
ORDER_INSTANT_COMPLETED | orderId=uuid | userId=uuid | quantity=1000 | cost=2.00 | balanceBefore=1000.00 | balanceAfter=998.00
ORDER_IDEMPOTENT_HIT | orderId=uuid | idempotencyKey=my-panel-order-12345
ORDER_CAPACITY_REJECTED | quantity=50000 | available=20448
ORDER_INSUFFICIENT_BALANCE | userId=uuid | required=200.00 | available=10.00
```

---

## Quick Reset for Re-Testing

```sql
-- Reset user balance
UPDATE users SET balance = 1000.00 WHERE api_key = 'test-api-key-local-dev-12345';

-- Clear test orders (optional)
DELETE FROM balance_transactions WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (SELECT id FROM users WHERE api_key = 'test-api-key-local-dev-12345'));
DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE api_key = 'test-api-key-local-dev-12345');
```

---

## Production vs Local/Dev Behavior

| Behavior | Local/Dev Profile | Production Profile |
|----------|-------------------|-------------------|
| Instant execution (≤1k) | ✅ YES - delivered=quantity, status=COMPLETED | ❌ NO - delivered=0, status=PENDING |
| Idempotency | ✅ Works | ✅ Works |
| Atomic transactions | ✅ Works | ✅ Works |
| Input validation | ✅ Works | ✅ Works |
| Capacity checks | ✅ Works | ✅ Works |

---

## Summary

✅ All tests passing = 1k order path is production-ready
- Orders are atomically created with balance deduction
- Idempotency keys prevent double-charges on retries
- Input validation catches bad requests early
- In local/dev, orders auto-complete for instant feedback
