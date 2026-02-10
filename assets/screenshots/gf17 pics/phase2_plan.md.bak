# Phase 2: Integration Tests & Service Wiring (5 weeks)

## Overview

Phase 2 transitions from **isolated unit tests** (Phase 1) to **integrated service architecture**. You'll:

1. **Phase 2a (Weeks 1–2):** Build the integration test harness with testcontainers
2. **Phase 2b (Weeks 3–4):** Wire Phase 1 logic into real Spring services
3. **Phase 2c (Week 5):** Add chaos + load tests, validate resilience

**Key Principle:** Tests drive design. Integration tests reveal service contracts that Phase 1 unit tests validate.

---

## Phase 2a: Integration Test Harness (Weeks 1–2)

### Goal
Write **one happy-path integration test** that validates the entire system end-to-end: Customer order → ProxyRouter selection → Worker execution → Spotify delivery → Refund computation.

### Setup: Testcontainers

```yaml
# docker-compose-test.yml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: goodfellaz17_test
      POSTGRES_PASSWORD: test
    ports:
      - "5432:5432"
  
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

### Test Structure

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class GoodFellaz17IntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName("goodfellaz17_test")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @Autowired
    TestRestTemplate client;
    
    @Autowired
    OrderRepository orderRepository;
    
    @Autowired
    ProxyRepository proxyRepository;
    
    @Test
    void happy_path_customer_order_to_refund() {
        // ARRANGE: Insert seed data
        List<ProxyNode> healthyProxies = List.of(
            new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.HEALTHY, 10),
            new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.HEALTHY, 45),
            new ProxyNode("192.168.1.3", 8080, "DE", ProxyStatus.HEALTHY, 80)
        );
        proxyRepository.saveAll(healthyProxies);
        
        // ACT: Submit order
        OrderRequest request = new OrderRequest(2000, "€0.002", "DE");
        ResponseEntity<OrderResponse> response = client.postForEntity("/api/orders", request, OrderResponse.class);
        
        Long orderId = response.getBody().getOrderId();
        
        // ASSERT: Order is PENDING
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(2000, order.getQuantity());
        
        // ACT: Trigger worker (via scheduler or direct call)
        workerScheduler.executeAllTasks();
        
        // ASSERT: Tasks are RUNNING
        List<OrderTask> tasks = taskRepository.findByOrderId(orderId);
        assertTrue(tasks.size() == 5); // 5 tasks × 400 plays
        assertTrue(tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.RUNNING));
        
        // ACT: Simulate Spotify deliveries (mock HTTP boundary only)
        mockSpotifyAPI.respondWith(200_OK); // 1200 plays succeed, 800 fail randomly
        taskExecutor.executeAll();
        
        // ASSERT: Order is COMPLETED, refund is computed
        order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        
        // Verify invariants
        int delivered = tasks.stream().mapToInt(OrderTask::getDelivered).sum();
        int failed = tasks.stream().mapToInt(OrderTask::getFailed).sum();
        int pending = tasks.stream().mapToInt(OrderTask::getPending).sum();
        
        assertEquals(2000, delivered + failed + pending); // Quantity conservation
        
        // Verify refund
        BigDecimal expectedRefund = BigDecimal.valueOf(failed).multiply(BigDecimal.valueOf(0.002))
            .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedRefund, order.getRefundAmount());
        
        // Verify balance
        BigDecimal expectedBalance = BigDecimal.valueOf(2000).multiply(BigDecimal.valueOf(0.002))
            .subtract(expectedRefund);
        assertEquals(expectedBalance, order.getCurrentBalance());
    }
}
```

### Week 1 Deliverables
- [ ] `docker-compose-test.yml` created, Docker images pull successfully
- [ ] `GoodFellaz17IntegrationTest` class created, test compiles
- [ ] Test runs but **fails** at first assertion (expected—you haven't wired services yet)
- [ ] Seed data insertion works (5 healthy proxies inserted to PostgreSQL)

### Week 2 Deliverables
- [ ] ProxyRouter fully wired (selectProxy returns healthy proxy)
- [ ] Integration test progresses to Worker assertion stage
- [ ] All Phase 1 unit tests still pass (no regressions)

---

## Phase 2b: Service Implementation (Weeks 3–4)

### Service 1: ProxyRouter

**Contract (from Test 1.1):**
```java
public interface ProxySelector {
    ProxyNode select(List<ProxyNode> candidates) throws NoAvailableProxyException;
}

public interface ProxyRouter {
    ProxyNode selectProxy(String region) throws NoAvailableProxyException;
}
```

**Implementation:**
```java
@Service
public class ProxyRouter {
    
    @Autowired
    private ProxySelector selector; // Generated by MontiCore
    
    @Autowired
    private ProxyRepository proxyRepository;
    
    public ProxyNode selectProxy(String region) {
        // Query DB for healthy proxies in region
        List<ProxyNode> candidates = proxyRepository.findHealthyByRegion(region);
        
        // Delegate to MontiCore-generated selector
        return selector.select(candidates);
    }
}
```

### Service 2: OrderService

**Contract (from Tests 3.1-3.7):**
```java
public interface OrderService {
    Order submitOrder(OrderRequest request) throws InvalidOrderException;
}
```

**Implementation:**
```java
@Service
@Transactional
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderInvariantValidator validator; // Generated by MontiCore
    
    @Autowired
    private OrderTaskService taskService;
    
    public Order submitOrder(OrderRequest request) {
        // Build order from request
        Order order = Order.from(request);
        
        // Validate invariants BEFORE persisting
        validator.validate(order);
        
        // Persist order
        orderRepository.save(order);
        
        // Create 5 tasks (quantity / 5)
        taskService.createTasks(order);
        
        return order;
    }
}
```

### Service 3: Worker

**Contract (from Sequence Diagram):**
```java
public interface Worker {
    void executeTask(OrderTask task);
}
```

**Implementation:**
```java
@Service
public class Worker {
    
    @Autowired
    private ProxyRouter proxyRouter;
    
    @Autowired
    private SpotifyClient spotify;
    
    @Autowired
    private OrderTaskRepository taskRepository;
    
    @Autowired
    private ProxyMetricsService metrics;
    
    @Transactional
    public void executeTask(OrderTask task) {
        try {
            // Select proxy (via ProxyRouter)
            ProxyNode proxy = proxyRouter.selectProxy(task.getRegion());
            
            // Execute plays via proxy
            int delivered = spotify.playViaBatch(task.getTrackIds(), proxy);
            
            // Update task
            task.setDelivered(delivered);
            task.setFailed(task.getQuantity() - delivered);
            task.setStatus(TaskStatus.COMPLETED);
            
            // Update proxy metrics
            metrics.recordSuccess(proxy.getId(), delivered);
            
        } catch (NetworkException e) {
            task.setStatus(TaskStatus.FAILED_TEMPORARY);
            metrics.recordFailure(proxy.getId(), task.getQuantity());
            throw new RuntimeException("Retry", e);
        }
        
        taskRepository.save(task);
    }
}
```

### Service 4: RefundService

**Contract (from Tests 3.2-3.7):**
```java
public interface RefundService {
    void issueRefund(Order order);
}
```

**Implementation:**
```java
@Service
@Transactional
public class RefundService {
    
    @Autowired
    private OrderInvariantValidator validator; // Generated by MontiCore
    
    @Autowired
    private OrderRepository orderRepository;
    
    public void issueRefund(Order order) {
        // Guard: Refund already issued?
        if (order.getRefundIssuedAt() != null) {
            return; // Idempotent: already done, exit early
        }
        
        // Compute failed count
        int failedCount = order.getFailed();
        
        // Compute refund via MontiCore validator
        BigDecimal refund = validator.computeRefund(failedCount, order.getUnitPrice());
        
        // Update order
        order.setRefundAmount(refund);
        order.setRefundIssuedAt(LocalDateTime.now());
        
        // Validate invariants (balance conservation, precision)
        validator.validate(order);
        
        orderRepository.save(order);
    }
}
```

### Week 3 Deliverables
- [ ] ProxyRouter fully wired + Worker executes tasks successfully
- [ ] Integration test progresses to refund computation
- [ ] ProxyMetrics cache (Redis) updates correctly

### Week 4 Deliverables
- [ ] RefundService wired + refunds computed correctly
- [ ] Integration test **PASSES** (happy path GREEN ✅)
- [ ] All Phase 1 unit tests still pass
- [ ] Coverage: 85%+ (integration test fills gaps unit tests miss)

---

## Phase 2c: Resilience & Chaos (Week 5)

### Scenario 1: Happy Path (Already Passing)
- 2000 plays ordered, 1200 delivered, 800 failed, €1.60 refund issued

### Scenario 2: Partial Failure
```java
@Test
void scenario_two_proxies_degrade_mid_execution() {
    // Similar setup, but kill 2/5 proxies mid-test
    // Verify: Remaining 3 proxies absorb load
    // Verify: Degraded proxies marked DEGRADED, new tasks routed to healthy ones
    // Verify: Partial refund issued for tasks that failed due to DEGRADED status
}
```

### Scenario 3: Complete Failure (All Proxies Offline)
```java
@Test
void scenario_all_proxies_offline_graceful_failure() {
    // Kill all proxies before execution
    // Verify: ProxyRouter throws NoAvailableProxyException
    // Verify: Worker catches, marks task FAILED_TEMPORARY
    // Verify: Task retries after backoff
    // Verify: If max retries exceeded, task marked FAILED_PERMANENT
    // Verify: Refund computed for all failed plays
}
```

### Load Test
```bash
# Use JMeter or custom load test
for i in {1..1000}; do
  curl -X POST http://localhost:8081/api/orders \
    -H "Content-Type: application/json" \
    -d '{"quantity": 2000, "price": "0.002", "region": "DE"}' &
done
wait

# Verify: All 1000 orders completed
# Verify: Proxy pool balanced (no single proxy >80% load)
# Verify: All refunds accurate (no money lost)
# Coverage target: 95%+
```

### Week 5 Deliverables
- [ ] 3 E2E test scenarios passing (happy path, partial failure, complete failure)
- [ ] Load test passes (1000 concurrent orders, no refund errors)
- [ ] Coverage report: 95%+
- [ ] Phase 2 complete, system ready for production validation

---

## Testing Checklist: Phase 2 Readiness

### Before Phase 2a
- [ ] All Phase 1 unit tests passing (21/21 GREEN ✅)
- [ ] MontiCore code generation verified (ProxySelector, HealthRules, Invariants)
- [ ] PostgreSQL + Redis Docker images tested locally

### Before Phase 2b
- [ ] Integration test harness compiles and runs (even if failing)
- [ ] Testcontainers correctly spin up PostgreSQL + Redis
- [ ] Seed data insertion verified (5 proxies in DB)
- [ ] Spring Boot application starts in test context

### Before Phase 2c
- [ ] Happy path integration test passes (Order → Refund complete)
- [ ] All Phase 1 unit tests still pass (no regressions)
- [ ] Service implementations match Phase 1 test contracts
- [ ] Code coverage 85%+

### Production Readiness Checklist
- [ ] All E2E scenarios passing (happy path, partial failure, complete failure)
- [ ] Load test successful (1000+ orders, <5% error rate)
- [ ] Coverage 95%+
- [ ] MontiCore CoCos validated (no invalid orders reach DB)
- [ ] Prometheus metrics exported (for monitoring)
- [ ] Graceful degradation verified (system remains usable under partial failure)

---

## Key Insights for Your Thesis

**What Phase 2 Demonstrates:**

1. **Test-First Design:** Phase 1 unit tests drive Phase 2 service architecture. No design guessing.

2. **Monticore Validation:** Generated code (ProxySelector, HealthRules, Invariants) is **verified** by unit tests before being used in production services.

3. **Layered Resilience:** 
   - Unit tests validate domain logic in isolation
   - Integration tests validate service composition
   - E2E/load tests validate infrastructure resilience

4. **Auditable Financial Correctness:** BigDecimal precision + idempotence guards ensure no money is lost, even on retry/failure scenarios.

5. **Self-Hosted Validation:** Using testcontainers + Docker Compose proves your system can run on commodity infrastructure without external services.

---

## References

- [Testcontainers Java Guide](https://www.testcontainers.org/)
- [Spring Boot Testing](https://spring.io/guides/gs/testing-web/)
- [JUnit 5 Parameterized Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests)
- [Test Pyramid (Martin Fowler)](https://martinfowler.com/bliki/TestPyramid.html)
