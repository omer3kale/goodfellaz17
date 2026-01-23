# Phase 1: Complete Test Specification (7 tests × 3 clusters)

## Context: Generative Software Engineering with MontiCore

**Course:** Generative Software Engineering (Data Science Masters, RWTH)  
**Project:** Spotify Bot Delivery System (Self-Hosted Infrastructure)  
**Target Coverage:** 65-70% baseline → 87% (by end of Phase 1)  
**Test Framework:** JUnit 5 + Mockito + Spring Boot Test

---

## Overview: Three Test Clusters

| Cluster | Domain | Tests | MontiCore Role | Spring Role |
|---------|--------|-------|-----------------|------------|
| **ProxySelector** | Proxy routing logic | 7 | Generates selector interface + defaults | Injects selector into router |
| **ProxyHealthRules** | Health state machine | 7 | Generates degradation thresholds | Runs monitor scheduler |
| **OrderInvariants** | Financial correctness | 7 | Generates refund/balance rules | Validates on order submit |

Each cluster is **pure unit testable** (no DB, no network), enabling fast feedback loops.

---

## CLUSTER 1: ProxySelector Tests

**Goal:** Ensure `ProxyRouter` always picks the correct proxy given availability, load, and tier constraints.

**MontiCore Role:**  
- Generates base `ProxySelector` interface.  
- Generates `LeastLoadedHealthySelector` default implementation.  
- Students override `select()` method via Monticore-generated subclass to implement custom strategies (round-robin, weighted, etc.).

**Spring Role:**  
- `@Component ProxySelectorImpl extends LeastLoadedHealthySelector`  
- Injected into `ProxyRouter.selectProxy()`.

---

### Test 1.1: Happy Path – Pick Least Loaded Healthy Proxy

```java
@Test
void test_happy_path_pick_least_loaded_healthy() {
    // Arrange
    ProxyNode node1 = new ProxyNode("192.168.1.1", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 45); // load=45
    ProxyNode node2 = new ProxyNode("192.168.1.2", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 10); // load=10
    ProxyNode node3 = new ProxyNode("192.168.1.3", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 80); // load=80
    List<ProxyNode> candidates = Arrays.asList(node1, node2, node3);
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act
    ProxyNode selected = selector.select(candidates);
    
    // Assert
    assertEquals(node2.getId(), selected.getId());
    assertEquals(10, selected.getCurrentLoad());
    assertEquals(ProxyStatus.HEALTHY, selected.getStatus());
}
```

**What's tested:**  
- Selector correctly identifies **lowest current_load** among HEALTHY proxies.  
- Returns the exact proxy node, not just IP/port.

**MontiCore extension point:**  
- Student can override `select()` in a generated `WeightedSelector extends LeastLoadedHealthySelector` that uses a weighted load formula.

---

### Test 1.2: Prefer Healthy Over Degraded

```java
@Test
void test_prefer_healthy_over_degraded() {
    // Arrange
    ProxyNode healthy = new ProxyNode("192.168.1.1", 8080, "DE", 
                                      ProxyStatus.HEALTHY, 5);
    ProxyNode degraded = new ProxyNode("192.168.1.2", 8080, "DE", 
                                       ProxyStatus.DEGRADED, 1); // Lower load, but DEGRADED
    List<ProxyNode> candidates = Arrays.asList(healthy, degraded);
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act
    ProxyNode selected = selector.select(candidates);
    
    // Assert
    assertEquals(healthy.getId(), selected.getId());
    assertEquals(ProxyStatus.HEALTHY, selected.getStatus());
}
```

**What's tested:**  
- Selector **prioritizes status over load**.  
- Even if DEGRADED has lower load, HEALTHY is preferred.

**MontiCore extension point:**  
- Student can generate a `FallbackSelector` that tries HEALTHY first, then DEGRADED if no HEALTHY available.

---

### Test 1.3: Fall Back to Degraded When No Healthy Proxies

```java
@Test
void test_fall_back_to_degraded_only() {
    // Arrange
    ProxyNode degraded1 = new ProxyNode("192.168.1.1", 8080, "DE", 
                                        ProxyStatus.DEGRADED, 60);
    ProxyNode degraded2 = new ProxyNode("192.168.1.2", 8080, "DE", 
                                        ProxyStatus.DEGRADED, 20); // Lower load
    List<ProxyNode> candidates = Arrays.asList(degraded1, degraded2);
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act
    ProxyNode selected = selector.select(candidates);
    
    // Assert
    assertEquals(degraded2.getId(), selected.getId());
    assertEquals(ProxyStatus.DEGRADED, selected.getStatus());
}
```

**What's tested:**  
- When **no HEALTHY proxies exist**, selector falls back to least-loaded DEGRADED.  
- Still respects load ordering within DEGRADED tier.

**MontiCore extension point:**  
- Students can generate a `DegradedFallbackRule` DSL object with a threshold (e.g., "use DEGRADED only if success_rate > 60%").

---

### Test 1.4: No Healthy Proxies – Throws Exception

```java
@Test
void test_no_healthy_proxies_throws_exception() {
    // Arrange
    ProxyNode offline1 = new ProxyNode("192.168.1.1", 8080, "DE", 
                                       ProxyStatus.OFFLINE, 0);
    ProxyNode offline2 = new ProxyNode("192.168.1.2", 8080, "DE", 
                                       ProxyStatus.OFFLINE, 0);
    List<ProxyNode> candidates = Arrays.asList(offline1, offline2);
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act & Assert
    assertThrows(NoAvailableProxyException.class, 
                 () -> selector.select(candidates));
}
```

**What's tested:**  
- If **all proxies are OFFLINE**, selector throws `NoAvailableProxyException`.  
- Application can catch and alert ops / queue the order.

**MontiCore extension point:**  
- Students generate an `ErrorHandler` rule that auto-escalates alerts when exception occurs.

---

### Test 1.5: Empty Proxy List – Throws Exception

```java
@Test
void test_empty_proxy_list_throws_exception() {
    // Arrange
    List<ProxyNode> candidates = Collections.emptyList();
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act & Assert
    assertThrows(NoAvailableProxyException.class, 
                 () -> selector.select(candidates));
}
```

**What's tested:**  
- Edge case: if **no proxies exist in the pool** (maybe all are being rebuilt), throw exception.  
- Prevents null pointer and makes failure mode explicit.

**Monticore extension point:**  
- Students can generate a `GracefulShutdown` rule that pauses order intake when proxy pool is empty.

---

### Test 1.6: Tie-Break by Stable Sort (Same Load)

```java
@Test
void test_tie_break_by_stable_sort() {
    // Arrange
    ProxyNode nodeA = new ProxyNode("192.168.1.1", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 50); // Same load
    ProxyNode nodeB = new ProxyNode("192.168.1.2", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 50); // Same load
    ProxyNode nodeC = new ProxyNode("192.168.1.3", 8080, "DE", 
                                    ProxyStatus.HEALTHY, 50); // Same load
    List<ProxyNode> candidates = Arrays.asList(nodeA, nodeB, nodeC);
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act
    ProxyNode selected1 = selector.select(candidates);
    ProxyNode selected2 = selector.select(candidates);
    ProxyNode selected3 = selector.select(candidates);
    
    // Assert
    // All three have same load, so first by insertion order or by ID
    assertEquals(nodeA.getId(), selected1.getId()); // Stable: always first
}
```

**What's tested:**  
- When **multiple proxies have identical load**, selector picks the **same one consistently** (not random).  
- Ensures reproducibility and fair load distribution.

**MontiCore extension point:**  
- Students generate a `RoundRobinTieBreaker` that rotates among tied proxies over time.

---

### Test 1.7: Region Filter Before Selection

```java
@Test
void test_region_filter_before_selection() {
    // Arrange
    ProxyNode deProxy = new ProxyNode("192.168.1.1", 8080, "DE", 
                                      ProxyStatus.HEALTHY, 5);
    ProxyNode usProxy = new ProxyNode("192.168.1.2", 8080, "US", 
                                      ProxyStatus.HEALTHY, 1);
    ProxyNode gbProxy = new ProxyNode("192.168.1.3", 8080, "GB", 
                                      ProxyStatus.HEALTHY, 2);
    List<ProxyNode> allCandidates = Arrays.asList(deProxy, usProxy, gbProxy);
    
    // Filter to DE region only
    List<ProxyNode> deCandidates = allCandidates.stream()
        .filter(p -> p.getRegion().equals("DE"))
        .collect(Collectors.toList());
    
    ProxySelector selector = new LeastLoadedHealthySelector();
    
    // Act
    ProxyNode selected = selector.select(deCandidates);
    
    // Assert
    assertEquals(deProxy.getId(), selected.getId());
    assertEquals("DE", selected.getRegion());
}
```

**What's tested:**  
- Selector works correctly when **candidates are pre-filtered by region**.  
- Ensures `ProxyRouter` can handle region-aware routing (e.g., "deliver via DE proxies only for German users").

**MontiCore extension point:**  
- Students generate a `RegionAffinityRule` DSL that auto-filters candidates based on user location.

---

## CLUSTER 2: ProxyHealthRules Tests

**Goal:** Ensure the health state machine (HEALTHY → DEGRADED → OFFLINE) transitions correctly based on metrics.

**MontiCore Role:**  
- Generates base `ProxyHealthRules` interface.  
- Generates `HealthStateTransition` enum with threshold configs.  
- Students override `evaluateTransition()` method to implement custom health policies.

**Spring Role:**  
- `ProxyHealthMonitor` runs periodically via `@Scheduled`.  
- Reads latest `proxy_metrics` from DB.  
- Calls `HealthRules.next()` to determine new state.  
- Updates `proxy_nodes.health_state` (or DB trigger does it).

---

### Test 2.1: HEALTHY + Good Metrics → Stay HEALTHY

```java
@Test
void test_healthy_with_good_metrics_stays_healthy() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.HEALTHY;
    float successRate = 98.5f;  // Good: >= 95%
    int availableConnections = 25; // Good: >= 10
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate, 
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.HEALTHY, nextStatus);
}
```

**What's tested:**  
- Proxy stays HEALTHY as long as **success_rate >= threshold AND connections available**.  
- No unnecessary state churn.

**MontiCore extension point:**  
- Students generate a `HealthyStabilityRule` that requires 5 consecutive good readings before returning to HEALTHY (hysteresis).

---

### Test 2.2: HEALTHY + Bad Success Rate → DEGRADED

```java
@Test
void test_healthy_bad_success_rate_becomes_degraded() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.HEALTHY;
    float successRate = 72.0f;  // Bad: < 95%
    int availableConnections = 15; // Still good
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.DEGRADED, nextStatus);
}
```

**What's tested:**  
- When **success_rate drops below threshold**, proxy becomes DEGRADED immediately.  
- Even if connections are available, poor performance triggers downgrade.

**MontiCore extension point:**  
- Students generate a `SuccessRateThreshold` DSL parameter that can be tuned per tier (PREMIUM=98%, STANDARD=90%).

---

### Test 2.3: DEGRADED + Recovered Metrics → HEALTHY

```java
@Test
void test_degraded_with_recovered_metrics_becomes_healthy() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.DEGRADED;
    float successRate = 97.0f;  // Recovered: >= 95%
    int availableConnections = 20; // Sufficient
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.HEALTHY, nextStatus);
}
```

**What's tested:**  
- Proxy can **auto-recover** from DEGRADED to HEALTHY when metrics improve.  
- Enables resilience without manual ops intervention.

**MontiCore extension point:**  
- Students generate a `RecoveryTimeRule` that requires a grace period (e.g., 5 minutes of good metrics) before accepting recovery.

---

### Test 2.4: OFFLINE Never Auto-Recovers

```java
@Test
void test_offline_never_auto_recovers() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.OFFLINE;
    float successRate = 99.5f;  // Excellent (if it were online)
    int availableConnections = 50; // Excellent
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.OFFLINE, nextStatus); // Still OFFLINE
}
```

**What's tested:**  
- OFFLINE is a **terminal state** that requires manual intervention (ops must explicitly reboot/redeploy).  
- Prevents thrashing if a proxy is in a persistent bad state (DNS issues, provider outage, etc.).

**MontiCore extension point:**  
- Students generate an `OfflineRecoveryPolicy` DSL that requires an explicit `recover` command with timestamp.

---

### Test 2.5: Overloaded Connection Pool Triggers DEGRADED

```java
@Test
void test_overloaded_connections_triggers_degraded() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.HEALTHY;
    float successRate = 96.0f;  // Good
    int availableConnections = 2; // BAD: < 10 threshold
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.DEGRADED, nextStatus);
}
```

**What's tested:**  
- Proxy can become DEGRADED if **connection pool is exhausted**, even with high success rate.  
- Prevents overload collapse.

**MontiCore extension point:**  
- Students generate a `ConnectionPoolWatchRule` that monitors available_connections and scales worker threads if needed.

---

### Test 2.6: Threshold Boundary (99.9% Success Rate)

```java
@Test
void test_threshold_boundary_99_9_percent_success() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.DEGRADED;
    float successRate = 99.9f;  // Excellent
    int availableConnections = 15;
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.HEALTHY, nextStatus);
}
```

**What's tested:**  
- **Boundary condition**: success_rate = 99.9% is well above 95% threshold.  
- Proxy recovers to HEALTHY.

**MontiCore extension point:**  
- Students generate a `BoundaryTestRule` CoCo that validates all thresholds are consistent (e.g., success_rate >= connection threshold).

---

### Test 2.7: Multiple Degradation Signals (both bad)

```java
@Test
void test_multiple_degradation_signals() {
    // Arrange
    ProxyStatus currentStatus = ProxyStatus.HEALTHY;
    float successRate = 70.0f;  // BAD
    int availableConnections = 3; // BAD
    ProxyHealthConfig config = new ProxyHealthConfig(
        successRateThreshold = 95.0f,
        minAvailableConnections = 10
    );
    ProxyHealthRules rules = new DefaultProxyHealthRules();
    
    // Act
    ProxyStatus nextStatus = rules.next(currentStatus, successRate,
                                        availableConnections, config);
    
    // Assert
    assertEquals(ProxyStatus.DEGRADED, nextStatus);
    // Severity: proxy has TWO failure signals, so degradation is immediate
}
```

**What's tested:**  
- When **multiple metrics are bad simultaneously**, proxy becomes DEGRADED.  
- Compound failures trigger faster response.

**MontiCore extension point:**  
- Students generate a `CompoundFailureAggregator` that assigns severity scores (success_rate=40%, connection=20%, total=60% degraded).

---

## CLUSTER 3: OrderInvariants Tests

**Goal:** Ensure **refund math** and **financial correctness** are guaranteed for every order.

**MontiCore Role:**  
- Generates base `OrderInvariantValidator` interface.  
- Generates `RefundCalculator` with base formulas.  
- Students override `validate()` method to implement custom rules (e.g., percentage refunds vs. fixed).

**Spring Role:**  
- `OrderService.submitOrder()` calls `invariant.validate()` before committing.  
- Prevents invalid order state from entering the DB.

---

### Test 3.1: Quantity Conservation (ordered == delivered + failed + pending)

```java
@Test
void test_quantity_conservation_ordered_equals_delivered_plus_failed_plus_pending() {
    // Arrange
    int ordered = 2000;
    int delivered = 800;
    int failedPermanent = 400;
    int pending = 800;
    
    // Act
    boolean conserved = OrderInvariants.quantityConserved(
        ordered, delivered, failedPermanent, pending
    );
    
    // Assert
    assertTrue(conserved);
    assertEquals(ordered, delivered + failedPermanent + pending);
}
```

**What's tested:**  
- **Core invariant**: All plays must be accounted for (delivered + failed + pending).  
- No plays disappear or are double-counted.

**MontiCore extension point:**  
- Students generate a `QuantityConservationCoCo` that validates this on every order state transition.

---

### Test 3.2: Refund Proportionality (unit price × failed count)

```java
@Test
void test_refund_proportionality_unit_price_times_failed() {
    // Arrange
    int failedCount = 800;
    BigDecimal unitPrice = BigDecimal.valueOf(0.002); // €0.002 per play
    
    // Act
    BigDecimal refundAmount = OrderInvariants.refundAmount(
        failedCount, unitPrice
    );
    
    // Assert
    BigDecimal expected = BigDecimal.valueOf(1.60); // 800 × 0.002
    assertEquals(0, expected.compareTo(refundAmount));
}
```

**What's tested:**  
- **Refund formula**: refund = unit_price × failed_count.  
- Exact to cents (2 decimal places).

**MontiCore extension point:**  
- Students generate a `RefundRuleAST` DSL that allows different formulas (e.g., 50% refund on DEGRADED failures).

---

### Test 3.3: Balance Conservation (income - refund = net)

```java
@Test
void test_balance_conservation_income_minus_refund_equals_net() {
    // Arrange
    BigDecimal totalIncome = BigDecimal.valueOf(4.00); // 2000 plays × 0.002
    BigDecimal refund = BigDecimal.valueOf(1.60);      // 800 failed plays
    
    // Act
    BigDecimal netBalance = totalIncome.subtract(refund);
    
    // Assert
    BigDecimal expected = BigDecimal.valueOf(2.40); // 4.00 - 1.60
    assertEquals(0, expected.compareTo(netBalance));
}
```

**What's tested:**  
- **Financial invariant**: income in, minus refunds out, equals net retained.  
- No money lost in the gap.

**MontiCore extension point:**  
- Students generate a `BalanceSheeting` rule that auto-logs this to an accounting ledger table.

---

### Test 3.4: Idempotent Refund (second call unchanged)

```java
@Test
void test_idempotent_refund_second_call_unchanged() {
    // Arrange
    Order order = new Order(1L, 2000, 800, BigDecimal.valueOf(0.002));
    order.setRefundIssuedAt(LocalDateTime.now().minusHours(1));
    order.setRefundAmount(BigDecimal.valueOf(1.60));
    BigDecimal balanceBefore = order.getCurrentBalance();
    
    // Act
    RefundService service = new RefundService();
    service.issueRefund(order); // First call already done (historical)
    service.issueRefund(order); // Second call (idempotent)
    
    // Assert
    assertEquals(balanceBefore, order.getCurrentBalance());
    // Balance unchanged: refund not applied twice
}
```

**What's tested:**  
- **Idempotence**: calling `issueRefund()` twice produces the same result as once.  
- Prevents double-refunding on retry.

**MontiCore extension point:**  
- Students generate an `IdempotenceGuard` CoCo that checks `refund_issued_at` is set.

---

### Test 3.5: Partial Refund Calculation (800 of 2000)

```java
@Test
void test_partial_refund_calculation_800_of_2000() {
    // Arrange
    int totalOrdered = 2000;
    int failedCount = 800;
    BigDecimal unitPrice = BigDecimal.valueOf(0.002);
    
    // Act
    BigDecimal refundAmount = OrderInvariants.refundAmount(
        failedCount, unitPrice
    );
    double refundPercentage = (failedCount / (double) totalOrdered) * 100;
    
    // Assert
    assertEquals(40.0, refundPercentage); // 40% of order failed
    assertEquals(BigDecimal.valueOf(1.60), refundAmount);
}
```

**What's tested:**  
- **Proportional refunding**: 800 failed out of 2000 = 40% refund.  
- Customer receives refund only for failed deliveries, not full amount.

**MontiCore extension point:**  
- Students generate a `PartialRefundRule` that allows customizing refund percentage by tier.

---

### Test 3.6: Zero Refund for Zero Failed

```java
@Test
void test_zero_refund_for_zero_failed() {
    // Arrange
    int failedCount = 0;
    BigDecimal unitPrice = BigDecimal.valueOf(0.002);
    
    // Act
    BigDecimal refundAmount = OrderInvariants.refundAmount(
        failedCount, unitPrice
    );
    
    // Assert
    assertEquals(BigDecimal.ZERO, refundAmount);
}
```

**What's tested:**  
- **Edge case**: if no plays failed, refund = €0.  
- Prevents spurious refunds on successful orders.

**MontiCore extension point:**  
- Students generate a `ZeroFailureHandling` CoCo that asserts refund == 0 when failedCount == 0.

---

### Test 3.7: Decimal Precision to Cents

```java
@Test
void test_decimal_precision_to_cents() {
    // Arrange
    int failedCount = 333;
    BigDecimal unitPrice = new BigDecimal("0.001"); // €0.001 per play (odd amount)
    
    // Act
    BigDecimal refundAmount = OrderInvariants.refundAmount(
        failedCount, unitPrice
    );
    
    // Assert
    // 333 × 0.001 = 0.333 → round to cents = €0.33
    assertEquals(2, refundAmount.scale()); // 2 decimal places
    assertEquals(new BigDecimal("0.33"), refundAmount.setScale(2, RoundingMode.HALF_UP));
}
```

**What's tested:**  
- **Monetary precision**: all amounts stored/returned to **exactly 2 decimal places** (cents).  
- Rounding is HALF_UP (standard banking).

**Monticore extension point:**  
- Students generate a `MonetaryPrecisionCoCo` that validates all BigDecimal fields have scale=2.

---

## Summary: Coverage Progression

| Phase | Cluster | Base Coverage | After Tests | Target |
|-------|---------|----------------|-------------|--------|
| **1.1** | Proxy setup | — | — | — |
| **1.2** | ProxySelector | 30% | 60% | 87% |
|  | ProxyHealthRules | 25% | 65% | 87% |
|  | OrderInvariants | 40% | 75% | 87% |
| **1.3+** | Integration + edge cases | 65–75% | 87%+ | ✅ |

---

## How to Run All 21 Tests

```bash
# Run all Phase 1 tests
mvn test -Dtest=ProxySelectorTest,ProxyHealthRulesTest,OrderInvariantsTest

# Run with coverage report
mvn clean test jacoco:report
# View: target/site/jacoco/index.html

# Run a single test cluster
mvn test -Dtest=ProxySelectorTest
```

---

## Teaching Notes for Tutorium

1. **Week 1:** Present Diagrams 1 & 2. Students understand DSL → code gen → test flow.
2. **Week 2:** Students implement Cluster 1 (ProxySelector). Easy wins, build confidence.
3. **Week 3:** Students implement Cluster 2 (ProxyHealthRules). Introduces state machines.
4. **Week 4:** Students implement Cluster 3 (OrderInvariants). Math-heavy, precise.
5. **Week 5:** Students generate MontiCore CoCos for validation (Diagram 3). Full generative cycle.

Each cluster has **7 tests**, so students complete ~21 tests / 5 weeks = 4–5 tests per week (realistic pace for a course).

---

## MontiCore DSL Example (Teaching)

```
// bot.dsl (Monticore grammar)
bot spotify_play {
    
    // Proxy tier definition
    proxy_tier PREMIUM {
        max_load = 100;
        min_available_connections = 15;
    }
    
    // Health state rules
    health_rule HEALTHY {
        success_rate >= 95%;
        available_connections >= min_available_connections;
    }
    
    health_rule DEGRADED {
        success_rate >= 80% && success_rate < 95%;
        available_connections >= 5;
    }
    
    // Refund rule (students override here)
    refund_rule PROPORTIONAL {
        refund_amount = unit_price * failed_count;
        precision = 2; // cents
    }
}
```

Students then generate:
```java
// Generated by MontiCore
public class BotConfigValidator extends AbstractCoCo<BotAST> {
    @Override
    public void check(BotAST ast) {
        // CoCo logic: validate health_rule thresholds are consistent
        assertTrue(HEALTHY.successRate > DEGRADED.successRate);
        // CoCo logic: validate refund_rule formula is computable
        assertNotNull(ast.getRefundRule().getFormula());
    }
}
```

---

## Questions for Students

1. **On ProxySelector:** "Why do we prefer HEALTHY over DEGRADED even if DEGRADED has lower load?"  
   *Answer: Risk — DEGRADED proxies fail more often, so the small load difference isn't worth the reliability cost.*

2. **On ProxyHealthRules:** "Why is OFFLINE terminal (no auto-recovery)?"  
   *Answer: If a proxy is OFFLINE, some underlying infra is broken (DNS, provider issue). Auto-recovery would waste requests. Better to escalate.*

3. **On OrderInvariants:** "What happens if a refund is issued twice?"  
   *Answer: The idempotence test catches it. The second call sees `refund_issued_at` is already set and returns without changing balance.*

---

## References

- [MontiCore Documentation](https://www.monticore.de/)  
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)  
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)  
- DDD patterns: Evans, E. (2003). *Domain-Driven Design*.
