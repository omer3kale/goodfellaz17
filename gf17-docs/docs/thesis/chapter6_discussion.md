# Chapter 6: Discussion and Future Work

## 6.1 What the Testing Strategy Achieved

This thesis demonstrated a principled approach to validating a financial, multi-proxy system using a combination of MontiCore-generated domain models and modern integration testing techniques. The key achievements are:

### 6.1.1 Defense-in-Depth Through Layered Testing

The test pyramid structure—21 unit tests supporting 5 integration tests—provides rapid feedback during development while maintaining realistic validation at the integration layer. A developer making changes to `OrderInvariants` receives feedback in under 1 second from unit tests; if those pass, the integration tests provide a second line of defense in under 2 minutes.

This layered approach caught two bugs during development that would have been missed by unit tests alone:

1. **Completion check omission:** The `OrderDeliveryWorker` incremented `failed_permanent_plays` but did not trigger the completion check, leaving orders stuck in `RUNNING` status indefinitely. Unit tests for the invariant passed because they tested the `Order` object directly; the integration test failed because the async workflow was incomplete.

2. **Remains not decremented:** The SQL statement for `atomicIncrementFailedPermanent()` updated `failed_permanent_plays` but forgot to decrement `remains`. Unit tests passed because mock repositories returned expected values; the integration test failed because the real PostgreSQL state violated INV-1.

Both bugs were caught by the Week 3 chaos test, demonstrating the value of realistic failure injection.

### 6.1.2 Invariants as Executable Contracts

Encoding invariants as executable assertions (`OrderInvariantValidator`) serves multiple purposes:

1. **Documentation:** The invariants are self-documenting. A new developer can read `INV-1: delivered + failed + remains = quantity` and understand the system's conservation law.

2. **Regression prevention:** Any change that breaks an invariant will fail at least one test. The invariants act as a safety net during refactoring.

3. **Cross-layer consistency:** The same `OrderInvariantValidator` is used in unit and integration tests, ensuring that the definition of "correct" is consistent across the test pyramid.

### 6.1.3 Testcontainers as a Production-Realistic Abstraction

By using real PostgreSQL and Redis containers, the test suite avoids the classic "it works on mocks" problem. The specific benefits observed:

- **SQL semantics validated:** `COALESCE`, `GREATEST`, and atomic `UPDATE` statements behave exactly as they would in production.
- **Connection pooling exercised:** R2DBC connection pooling under concurrent access is tested, not mocked.
- **Schema migrations validated:** Flyway migrations run against the test container, ensuring they will succeed in production.

The 7-second container startup time is an acceptable tradeoff for this level of realism.

---

## 6.2 What Remains

### 6.2.1 AppleMontiCore Integration

The MontiCore DSL used in this thesis generates Java domain entities from a grammar specification. However, the full vision of the GoodFellaz17 project includes "AppleMontiCore"—a hypothetical integration that would generate:

- Swift models for iOS client applications
- Type-safe API contracts shared between backend and frontend
- Automated DTO mapping between layers

This integration was not implemented due to time constraints. Future work could explore whether MontiCore's code generation can be extended to produce client-side artifacts.

### 6.2.2 Real Proxy Integration

The current test suite uses a `FailureInjectionService` to simulate proxy behavior. In production, the system would interact with real proxy providers (residential, datacenter, mobile). Testing against real proxies would require:

- Contractual agreements with proxy vendors for test traffic
- Isolation of test traffic from production quotas
- Handling of actual rate limits and bans from Spotify

A future E2E test suite could validate this layer using a dedicated test Spotify account and proxy pool.

### 6.2.3 Production Observability

The integration tests validate correctness but do not validate observability. A production-ready system should include:

- **Distributed tracing:** Trace IDs propagated through async task queues
- **Metrics dashboards:** Real-time visibility into order throughput, proxy health, refund rates
- **Alerting:** Automated alerts when invariants are violated in production (e.g., balance discrepancies)

These concerns are orthogonal to correctness testing and would require a separate validation effort.

---

## 6.3 Lessons Learned

### 6.3.1 DSL + Testcontainers: A Powerful Combination

The combination of MontiCore (domain modeling) and Testcontainers (realistic integration testing) proved highly effective:

- **MontiCore** generates boilerplate entity classes and enforces structural constraints at compile time.
- **Testcontainers** validates runtime behavior against real infrastructure.

This separation of concerns—compile-time structure vs. runtime behavior—reduces the testing burden. Unit tests focus on domain logic; integration tests focus on system composition.

### 6.3.2 Chaos Testing Requires Patience

The Week 3 test takes 95 seconds to complete. Initial attempts to speed up the test (reducing timeouts, increasing poll frequency) resulted in flaky failures. The lesson: async systems require async-appropriate test strategies. Awaitility's polling model, while slower than direct assertions, is the correct abstraction for eventually-consistent behavior.

### 6.3.3 Infrastructure Stability Matters

The Week 4 test was not completed due to Colima/Docker instability on the development machine. This highlights a broader lesson: CI/CD pipelines should run integration tests on standardized, stable infrastructure. Developer laptops are convenient but unreliable for long-running container workloads.

---

## 6.4 Regulatory Considerations

GoodFellaz17 operates in a gray area of the digital services market. While the testing methodology is sound, a production deployment would face regulatory scrutiny. This section briefly discusses how the testing strategy could be adapted for stricter compliance requirements.

### 6.4.1 Audit Logging

Financial regulations (PCI-DSS, SOX) require immutable audit trails. The current test suite validates correctness but not auditability. Future work could add:

- Append-only event logs for all balance mutations
- Cryptographic signatures for transaction records
- Integration tests that verify audit log completeness

### 6.4.2 Idempotence Under Network Partition

The current idempotence test (INV-6) validates that duplicate requests with the same idempotency key do not result in duplicate charges. However, it does not test network partitions where the client is uncertain whether the request succeeded. A more rigorous test would:

1. Submit an order
2. Kill the database connection mid-transaction
3. Retry the request
4. Verify that exactly one order was created

This requires infrastructure-level chaos injection (e.g., Toxiproxy) beyond the current `FailureInjectionService`.

### 6.4.3 Reconciliation and Reporting

Regulated systems require periodic reconciliation reports that prove invariants held over a time period. The current tests validate point-in-time correctness; a production system would need batch jobs that:

- Scan all orders from the past 24 hours
- Verify INV-1 through INV-6 for each
- Generate compliance reports

The `OrderInvariantValidator` could be reused for this purpose, demonstrating the value of encoding invariants as reusable components.

---

## 6.5 Conclusion

This thesis presented a testing methodology for validating a financial, multi-proxy system using:

1. **MontiCore DSL** for domain model generation
2. **Unit tests** for isolated domain logic (21 tests, 87% coverage)
3. **Testcontainers** for realistic infrastructure (PostgreSQL, Redis)
4. **Integration tests** for end-to-end workflows (5 tests)
5. **Invariants** as cross-cutting contracts validated at multiple layers

The methodology successfully caught two bugs that unit tests alone would have missed, demonstrating the value of integration testing against real infrastructure.

Future work includes AppleMontiCore client generation, real proxy integration, and production observability. The testing strategy described here provides a foundation that can be extended as the system evolves.

---

*[End of Chapter 6]*
