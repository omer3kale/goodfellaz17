# MSSQL End-to-End Health Check — Step-by-Step

## Goal
Prove that your Spring Boot app ↔ MSSQL ↔ FTL-B schema is working end-to-end.

---

## Step 1: Start MSSQL Server

```bash
docker-compose -f docker-compose.mssql.yml up -d
```

Wait for healthcheck to pass (Ctrl+C after you see "healthy"):
```bash
docker-compose -f docker-compose.mssql.yml logs goodfellaz17-mssql | grep -i healthy
```

---

## Step 2: Start Spring Boot with MSSQL Profile

```bash
mvn clean spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=mssql'
```

Watch for Flyway migrations (V15 should execute):
```
... o.f.c.internal.command.DbMigrate      : info: V15__Create_MSSQL_Core_Tables.sql migrated successfully
```

---

## Step 3: Test Database Health Check

```bash
curl -s http://localhost:8080/api/health/db | jq .
```

Expected response:
```json
{
  "status": "UP",
  "message": "MSSQL connection successful",
  "orderCount": 0,
  "timestamp": "2026-03-13T12:34:56.789Z"
}
```

✅ **If you see this, your app is connected to MSSQL!**

---

## Step 4: Manually Seed One Order + One Task

Connect to MSSQL:

```bash
docker exec -it goodfellaz17-mssql sqlcmd -S localhost -U sa -P 'YourStrong@Passw0rd'
```

Or use your VS Code SQL Server extension to connect:
- **Server:** localhost,1433
- **Login:** sa
- **Password:** YourStrong@Passw0rd

### SQL: Create UUIDs and insert test order

```sql
-- First, create a tenant (if not exists)
INSERT INTO tenants (id, name, created_at)
VALUES ('550e8400-e29b-41d4-a716-446655440001', 'Test Tenant', SYSDATETIME());

-- Create a service (SPOTIFY_PLAYS)
INSERT INTO services (id, code, description, created_at)
VALUES ('550e8400-e29b-41d4-a716-446655440002', 'SPOTIFY_PLAYS', 'Spotify play delivery', SYSDATETIME());

-- Create a price tier
INSERT INTO price_tiers (id, service_id, unit_cost, min_quantity, created_at)
VALUES ('550e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002', 0.13, 1000, SYSDATETIME());

-- Insert test order (status=PENDING)
INSERT INTO orders (
    id, tenant_id, service_id, track_id, artist_id, quantity,
    status, status_changed_at, price_tier_id, estimated_cost,
    plays_delivered, plays_failed, created_at
)
VALUES (
    '550e8400-e29b-41d4-a716-446655440100',
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440002',
    'spotify:track:123abc',
    'Taylor Swift',
    1000,
    'PENDING',
    SYSDATETIME(),
    '550e8400-e29b-41d4-a716-446655440003',
    0.13,
    0,
    0,
    SYSDATETIME()
);

-- Insert test task for that order (status=PENDING)
INSERT INTO order_tasks (
    id, order_id, tenant_id, status, quantity,
    retry_count, max_retries, created_at
)
VALUES (
    '550e8400-e29b-41d4-a716-446655440201',
    '550e8400-e29b-41d4-a716-446655440100',
    '550e8400-e29b-41d4-a716-446655440001',
    'PENDING',
    1,
    0,
    3,
    SYSDATETIME()
);

-- Verify insert
SELECT id, tenant_id, status, created_at FROM orders WHERE id = '550e8400-e29b-41d4-a716-446655440100';
```

---

## Step 5: Fetch the Order via REST API

```bash
curl -s http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440100 | jq .
```

Expected response:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440100",
  "tenantId": "550e8400-e29b-41d4-a716-446655440001",
  "serviceId": "550e8400-e29b-41d4-a716-446655440002",
  "trackId": "spotify:track:123abc",
  "artistId": "Taylor Swift",
  "quantity": 1000,
  "status": "PENDING",
  "statusChangedAt": "2026-03-13T12:34:56.789Z",
  "estimatedCost": "0.13",
  "actualCost": null,
  "playsDelivered": 0,
  "playsFailed": 0,
  "failureReason": null,
  "createdAt": "2026-03-13T12:34:56.789Z",
  "startedAt": null,
  "completedAt": null
}
```

✅ **If you see this, end-to-end is working!**

---

## Step 6: Check Database Health Again

```bash
curl -s http://localhost:8080/api/health/db | jq .
```

Should show:
```json
{
  "status": "UP",
  "message": "MSSQL connection successful",
  "orderCount": 1,
  "timestamp": "2026-03-13T12:34:56.789Z"
}
```

---

## Troubleshooting

### Connection Refused
```
Error: Database connection failed
```
→ Check MSSQL is running: `docker ps | grep mssql`  
→ Wait 30 seconds for healthcheck

### Login Failed
```
Login failed for user 'sa'
```
→ Verify password is `YourStrong@Passw0rd` in `docker-compose.mssql.yml`

### Flyway Migration Failed
```
Unable to execute migration
```
→ Check SQL syntax in V15__Create_MSSQL_Core_Tables.sql matches MSSQL (not PostgreSQL)

### Order Not Found (404)
```json
{
  "status": "NOT_FOUND",
  "message": "Order 550e8400... not found"
}
```
→ Verify INSERT succeeded in step 4  
→ Run: `SELECT * FROM orders;` in sqlcmd

---

## Next Steps (After Validating E2E)

Once this flows green, you're ready for:
1. **Option B**: Design `tenants` + `services` tables + seed reference data
2. **Option C**: Build R2DBC repositories for Order/OrderTask/ExecutionResult
3. **Option D**: Build TaskExecutionService that reads PENDING tasks and executes them

---

**Last Updated:** 2026-03-13  
**Status:** Ready for testing ✅
