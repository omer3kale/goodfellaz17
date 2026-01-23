# Chapter 5: Results

## 5.1 Test Execution Summary

Table 5.1 summarizes the test execution results across both phases of validation.

| Phase | Test Cluster | Test Count | Status | Execution Time |
|-------|--------------|------------|--------|----------------|
| **Phase 1** | ProxySelector | 7 | ✅ GREEN | ~0.3s |
| **Phase 1** | ProxyHealthRules | 7 | ✅ GREEN | ~0.2s |
| **Phase 1** | OrderInvariants | 7 | ✅ GREEN | ~0.4s |
| **Phase 2 Week 1** | Infrastructure | 3 | ✅ GREEN | ~7s |
| **Phase 2 Week 2** | Happy Path | 1 | ✅ GREEN | ~8s |
| **Phase 2 Week 3** | Chaos/Partial Failure | 1 | ✅ GREEN | ~95s |
| **Total** | — | **26** | **26/26 GREEN** | ~111s |

*Table 5.1: Test Execution Summary*

### Execution Environment

All tests were executed on the following configuration:

- **Hardware:** Apple M-series MacBook (ARM64)
- **Java:** OpenJDK 17.0.14
- **Spring Boot:** 3.5.x with WebFlux (reactive)
- **Database:** PostgreSQL 16-alpine via Testcontainers
- **Cache:** Redis 7-alpine via Testcontainers
- **Container Runtime:** Colima (Docker-compatible)
- **Test Framework:** JUnit 5 + Awaitility 4.2.0

### Why Week 3 Takes 95 Seconds

The Week 3 chaos test is intentionally slow. Unlike unit tests that execute in milliseconds, this test exercises real asynchronous behavior:

1. **Retry backoff:** Failed tasks are retried with exponential backoff (1s → 2s → 4s)
2. **Health rule evaluation:** Proxy health is re-evaluated every 5 seconds
3. **Awaitility polling:** Database state is polled every 2 seconds until convergence

This slow execution is a *feature*, not a bug. It approximates production reality where order fulfillment spans minutes, not milliseconds. A test that completes in 100ms would miss timing-dependent bugs that only manifest under realistic async loads.

---

## 5.2 Correctness of Money Flow

The integration tests validate that the system correctly handles financial operations across all execution paths. This section presents evidence for each money-flow invariant.

### 5.2.1 Quantity Conservation (INV-1)

**Invariant:** `delivered + failed_permanent + remains = quantity`

**Evidence from Week 2 (Happy Path):**

```
Order submitted: quantity=500
Final state:
  - delivered_plays: 500
  - failed_permanent_plays: 0
  - remains: 0
  - Sum: 500 ✓
```

**Evidence from Week 3 (Chaos):**

```
Order submitted: quantity=2000
Final state:
  - delivered_plays: 400
  - failed_permanent_plays: 1600
  - remains: 0
  - Sum: 2000 ✓
```

In both cases, no plays were created or destroyed—the quantity was conserved across delivery and failure paths.

### 5.2.2 Refund Proportionality (INV-3)

**Invariant:** `refunded_amount = failed_permanent_plays × unit_price`

**Evidence from Week 3:**

```
Failed plays: 1600
Unit price: $0.10
Expected refund: 1600 × 0.10 = $160.00
Actual refund: $160.00 ✓
```

The refund calculation correctly compensates for exactly the plays that could not be delivered, no more and no less.

### 5.2.3 Balance Conservation (INV-4)

**Invariant:** `final_balance = initial_balance - charged + refunded`

**Evidence from Week 2:**

```
Initial balance: $100.00
Charged: $50.00 (500 plays × $0.10)
Refunded: $0.00
Expected final: $100.00 - $50.00 + $0.00 = $50.00
Actual final: $50.00 ✓
```

**Evidence from Week 3:**

```
Initial balance: $500.00
Charged: $200.00 (2000 plays × $0.10)
Refunded: $160.00 (1600 failed × $0.10)
Expected final: $500.00 - $200.00 + $160.00 = $460.00
Actual final: $460.00 ✓
```

The user's balance is correctly maintained across charge and refund operations.

### 5.2.4 Idempotence (INV-6)

**Invariant:** Duplicate order submissions must not result in duplicate charges.

**Evidence from Week 2:**

The happy-path test includes an idempotence check:

1. Submit order with idempotency key `ORDER-TEST-001`
2. Receive HTTP 201, order created
3. Submit identical request with same idempotency key
4. Receive HTTP 409 Conflict (or HTTP 200 with same order ID)
5. Verify: user balance deducted exactly once

This prevents the "double-charge" bug that is common in payment systems when network retries cause duplicate submissions.

---

## 5.3 Resilience of Proxy Layer

### 5.3.1 Proxy Health State Transitions

The Week 3 test validates that the `ProxyHealthRules` logic (unit-tested in Phase 1) correctly governs proxy state transitions under load.

**Initial State:**
- 3 proxies: `ONLINE` (healthy)
- 2 proxies: `DEGRADED` (pre-seeded)

**Execution Observations:**

With a 90% failure rate injected, healthy proxies rapidly accumulated failures:

| Time | Proxy A | Proxy B | Proxy C |
|------|---------|---------|---------|
| T+0s | ONLINE | ONLINE | ONLINE |
| T+30s | DEGRADED | ONLINE | DEGRADED |
| T+60s | BANNED | DEGRADED | BANNED |
| T+95s | BANNED | BANNED | BANNED |

**Validation:**

1. **Degradation threshold:** Proxies transitioned to `DEGRADED` after exceeding 20% failure rate over 50 requests (as configured in `ProxyHealthConfig`)
2. **Ban threshold:** Proxies transitioned to `BANNED` after exceeding 50% failure rate over 100 requests
3. **Task routing:** After a proxy was banned, zero new tasks were assigned to it

### 5.3.2 Connection to Phase 1 Unit Tests

The proxy state transitions observed in Week 3 directly validate the rules tested in `ProxyHealthRulesTest`:

| Unit Test | Integration Observation |
|-----------|------------------------|
| `shouldDegradeAfterFailureThreshold()` | Proxies A and C degraded at T+30s |
| `shouldBanAfterSevereFailures()` | All proxies banned by T+95s |
| `shouldNotAssignTasksToBannedProxy()` | Task count for banned proxies = 0 after ban time |

This demonstrates that the unit-tested rules hold in a realistic execution environment with real database state and async workers.

---

## 5.4 Limitations

This section acknowledges the boundaries of what the test suite validates.

### 5.4.1 Week 4 Tests Not Executed

The original test plan included a Week 4 "total outage" scenario where all proxies fail simultaneously. This test was implemented but not executed to green status due to Docker/Colima infrastructure instability on the development machine. The test logic is present in the codebase and can be validated on more stable infrastructure.

**Impact:** The test suite does not fully validate behavior under catastrophic failure. However, the Week 3 test (90% failure rate, eventual total ban) approximates this scenario and all invariants held.

### 5.4.2 Single-Node Database

Tests run against a single PostgreSQL container, not a clustered deployment. This means the test suite does not validate:

- Replication lag during failover
- Split-brain scenarios
- Cross-region consistency

For a production deployment, additional chaos tests using tools like Toxiproxy or Chaos Monkey would be appropriate.

### 5.4.3 Sealed External API

The Spotify API interaction is handled by a deterministic executor that simulates responses based on the `FailureInjectionService` configuration. The test suite does not validate:

- Actual Spotify API rate limits
- OAuth token refresh under load
- API response parsing edge cases

This is a deliberate design choice: integration tests should not depend on external services. Validating real Spotify API behavior would require a separate E2E test suite with live credentials.

### 5.4.4 Coverage Metric

While Phase 1 achieved 87% line coverage, Phase 2 integration tests do not report coverage in the same way. Integration tests exercise the full stack but do not instrument individual classes. The value of integration tests lies in validating *behavior*, not maximizing coverage metrics.

---

## 5.5 Summary

The test suite validates that GoodFellaz17 correctly handles money flows and proxy failures:

| Property | Validation |
|----------|------------|
| Quantity is conserved | ✅ Unit + Integration |
| Refunds are proportional | ✅ Unit + Integration |
| Balances are consistent | ✅ Unit + Integration |
| Duplicate charges prevented | ✅ Integration |
| Proxies degrade correctly | ✅ Unit + Integration |
| Banned proxies receive no tasks | ✅ Unit + Integration |

The limitations are clearly bounded: Week 4 infrastructure issues, single-node database, sealed external API. These are acceptable for a thesis-grade demonstration of the testing methodology.

The next chapter discusses the implications of these results and outlines future work.

---

*[End of Chapter 5]*
