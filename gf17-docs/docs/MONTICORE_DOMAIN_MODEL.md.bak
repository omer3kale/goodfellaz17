# MontiCore Domain Model Integration Guide

## RWTH Aachen MATSE Thesis: goodfellaz17 Domain Architecture

This document describes the MontiCore grammar-based domain model system for the goodfellaz17 Spotify bot delivery engine.

---

## Overview

The domain model serves as the **single source of truth** for:

1. **goodfellaz17** - Core delivery engine
2. **botzzz773** - SMM panel frontend
3. **Market competitor site** - Black+neon green design

### Benefits

- **No field name drift** (userId vs uid, etc.)
- **Testable, reproducible schema** across environments
- **Extensible** for future features (fraud detection, geo-targeting)
- **Type-safe** code generation with validation

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     DomainModel.mc4                             │
│                   (MontiCore Grammar)                           │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Goodfellaz17.dm                              │
│               (Domain Model Instance)                           │
│                                                                 │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌────────────────────────┐ │
│  │  User   │ │  Order  │ │ Service │ │   BalanceTransaction   │ │
│  └─────────┘ └─────────┘ └─────────┘ └────────────────────────┘ │
│  ┌───────────┐ ┌──────────────┐ ┌────────────┐ ┌─────────────┐  │
│  │ ProxyNode │ │ ProxyMetrics │ │ DeviceNode │ │ TorCircuit  │  │
│  └───────────┘ └──────────────┘ └────────────┘ └─────────────┘  │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────┴─────────────────┐
        │         Code Generation           │
        └───┬─────────┬─────────┬───────┬───┘
            │         │         │       │
            ▼         ▼         ▼       ▼
       ┌────────┐ ┌────────┐ ┌─────┐ ┌────────────┐
       │  JPA   │ │  DTOs  │ │ DDL │ │ Repository │
       │Entities│ │        │ │     │ │ Interfaces │
       └────────┘ └────────┘ └─────┘ └────────────┘
```

---

## Generated Artifacts

### 1. JPA Entities

Location: `src/main/java/com/goodfellaz17/domain/model/generated/`

| Entity | Description |
|--------|-------------|
| `UserEntity` | Platform users with tiered pricing |
| `OrderEntity` | Delivery orders (aggregate root) |
| `ServiceEntity` | Spotify engagement services |
| `BalanceTransactionEntity` | Financial ledger |
| `ProxyNodeEntity` | Distributed proxy pool |
| `ProxyMetricsEntity` | Real-time proxy health |
| `DeviceNodeEntity` | Emulator/device farm |
| `TorCircuitEntity` | Tor exit circuits |

### 2. Enums

All enums are stored as **STRING** in the database for forward compatibility:

| Enum | Values |
|------|--------|
| `UserTier` | CONSUMER, RESELLER, AGENCY |
| `UserStatus` | ACTIVE, SUSPENDED, PENDING_VERIFICATION |
| `OrderStatus` | PENDING, VALIDATING, RUNNING, COMPLETED, PARTIAL, FAILED, REFUNDED, CANCELLED |
| `GeoProfile` | WORLDWIDE, USA, UK, DE, FR, BR, MX, LATAM, EUROPE, ASIA, PREMIUM_MIX |
| `ServiceType` | PLAYS, MONTHLY_LISTENERS, SAVES, FOLLOWS, PLAYLIST_FOLLOWERS, PLAYLIST_PLAYS |
| `ProxyTier` | DATACENTER, ISP, TOR, RESIDENTIAL, MOBILE |
| `ProxyStatus` | ONLINE, OFFLINE, MAINTENANCE, BANNED, RATE_LIMITED |
| `ProxyProvider` | VULTR, HETZNER, NETCUP, CONTABO, OVH, AWS, DIGITALOCEAN, LINODE |

### 3. DTOs

Location: `src/main/java/com/goodfellaz17/application/dto/generated/`

| DTO | Purpose |
|-----|---------|
| `CreateOrderRequest` | REST API order creation |
| `OrderResponse` | REST API order details |
| `UserRegistrationRequest` | User signup |
| `UserProfileResponse` | User profile API |
| `ProxyNodeStatus` | Admin dashboard proxy status |

### 4. Repositories

Location: `src/main/java/com/goodfellaz17/infrastructure/persistence/generated/`

| Repository | Features |
|------------|----------|
| `GeneratedOrderRepository` | CRUD + custom queries (active orders, pagination, progress updates) |
| `GeneratedUserRepository` | CRUD + auth queries (email, API key) + balance operations |
| `GeneratedProxyNodeRepository` | CRUD + proxy selection queries (by tier, country, health) |

### 5. DDL

Location: `src/main/resources/db/migration/V1__Initial_Schema.sql`

Also available as Java constants in `SchemaDDL.java` for programmatic use.

---

## Integration with HybridProxyRouter

The `HybridProxyRouterV2` integrates with the generated domain model:

```java
// Route a streaming request to USA proxies
RoutingRequest request = RoutingRequest.builder()
    .operation(OperationType.STREAMING)
    .targetCountry("US")
    .quantity(1000)
    .build();

Mono<ProxySelection> selection = proxyRouter.route(request);

selection.flatMap(proxy -> {
    // Use proxy for streaming
    return performStream(proxy.proxyUrl(), trackUrl)
        .doOnSuccess(result ->
            proxyRouter.reportResult(proxy.proxyId(),
                ProxyResult.success(result.latencyMs(), result.bytes()))
        )
        .doOnError(e ->
            proxyRouter.reportResult(proxy.proxyId(),
                ProxyResult.failure(0, 500))
        );
});
```

### Selection Algorithm

1. **Filter** by operation requirements (tier, geo)
2. **Query** candidates from `GeneratedProxyNodeRepository`
3. **Score** based on:
   - Success rate (from `ProxyMetrics`)
   - Latency P95
   - Current load vs capacity
   - Cost per request
4. **Apply circuit breaker** for failing tiers
5. **Weighted random selection** from top N for load distribution
6. **Track sticky sessions** for account operations

---

## Order + Service + BalanceTransaction Integration

The financial/delivery core uses three interconnected entities:

```java
// 1. Create order (deducts balance)
Mono<OrderResponse> createOrder(CreateOrderRequest request, UUID userId) {
    return serviceRepository.findById(request.serviceId())
        .flatMap(service -> {
            // Calculate cost based on user tier
            BigDecimal cost = calculateCost(service, request.quantity(), userTier);

            // Deduct balance
            return userRepository.updateBalance(userId, cost.negate())
                .flatMap(user -> {
                    // Create order
                    OrderEntity order = OrderEntity.builder()
                        .userId(userId)
                        .serviceId(service.getId())
                        .quantity(request.quantity())
                        .targetUrl(request.targetUrl())
                        .geoProfile(request.geoProfile())
                        .cost(cost)
                        .build();

                    return orderRepository.save(order);
                })
                .flatMap(order -> {
                    // Record transaction
                    BalanceTransaction tx = BalanceTransaction.builder()
                        .userId(userId)
                        .orderId(order.getId())
                        .amount(cost.negate())
                        .type(TransactionType.DEBIT.name())
                        .reason("Order #" + order.getId())
                        .build();

                    return transactionRepository.save(tx)
                        .thenReturn(OrderResponse.fromEntity(order, service.getName()));
                });
        });
}
```

---

## ProxyNode + ProxyMetrics for 15k/48-72h Guarantee

The proxy infrastructure tracks pool health:

```java
// Check if we can fulfill the 15k/48-72h package
public Mono<Boolean> canFulfillPackage(int quantity, String geoProfile, int maxDays) {
    return proxyRouter.getAllPoolHealth()
        .collectList()
        .map(pools -> {
            // Calculate total healthy capacity
            int healthyCapacity = pools.stream()
                .filter(PoolHealthStatus::isHealthy)
                .mapToInt(p -> p.totalCapacity() - p.currentLoad())
                .sum();

            // Estimate delivery rate (requests per day)
            int requestsPerDay = healthyCapacity * 24 * 60; // 1 req/min per slot

            // Can we deliver quantity in maxDays?
            return requestsPerDay * maxDays >= quantity;
        });
}
```

---

## Usage Examples

### Create a new order via API

```bash
POST /api/v1/orders
Authorization: Bearer <api_key>
Content-Type: application/json

{
    "serviceId": "550e8400-e29b-41d4-a716-446655440000",
    "quantity": 15000,
    "targetUrl": "https://open.spotify.com/track/abc123",
    "geoProfile": "USA",
    "speedMultiplier": 1.5
}
```

### Query proxy pool health

```java
proxyRouter.getAllPoolHealth()
    .doOnNext(status -> log.info(
        "Tier {}: {} nodes, {}% load, {}% success rate, circuit={}",
        status.tier(),
        status.nodeCount(),
        status.getLoadPercent(),
        status.successRate() * 100,
        status.circuitOpen() ? "OPEN" : "CLOSED"
    ))
    .subscribe();
```

---

## Build Commands

```bash
# Compile grammar and generate code
mvn generate-sources

# Run all tests including schema validation
mvn test

# Generate DDL for specific database
mvn exec:java -Dexec.mainClass="com.goodfellaz17.tools.SchemaGenerator" -Dexec.args="postgres"

# Run Flyway migration
mvn flyway:migrate
```

---

## Extending the Model

To add a new entity:

1. Add entity definition to `Goodfellaz17.dm`
2. Add DTOs for API contracts
3. Add repository definition
4. Run `mvn generate-sources`
5. Create Flyway migration `V{N}__Add_{Entity}.sql`

Example adding fraud detection:

```
entity FraudSignal {
    @Generated(UUID)
    id: UUID @NotNull;

    orderId: UUID @NotNull @Indexed;
    signalType: String @NotNull; // IP_MISMATCH, VELOCITY, BOT_PATTERN
    confidence: Double @NotNull @Range(min = 0.0, max = 1.0);
    details: String @Nullable @Length(max = 2000);
    detectedAt: Instant @NotNull @Default(Instant.now());

    @Index(name = "idx_fraud_signals_order", columns = [orderId])
}
```

---

## Contact

- **Project**: goodfellaz17
- **Institution**: RWTH Aachen, MATSE Program
- **Topic**: Distributed Proxy Orchestration and Anti-Detection Strategies
