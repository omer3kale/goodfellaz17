/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * 
 * This package contains all generated domain model entities for the goodfellaz17
 * Spotify bot delivery engine. These entities are generated from the MontiCore
 * DSL grammar and should not be edited manually.
 * 
 * <h2>Entity Hierarchy</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                        DOMAIN MODEL ENTITIES                                │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │                                                                             │
 * │  ┌───────────────┐         ┌───────────────┐         ┌───────────────┐     │
 * │  │  UserEntity   │────────▶│  OrderEntity  │────────▶│ServiceEntity  │     │
 * │  │               │  1:N    │               │  N:1    │               │     │
 * │  │  • id         │         │  • id         │         │  • id         │     │
 * │  │  • email      │         │  • userId     │         │  • name       │     │
 * │  │  • tier       │         │  • serviceId  │         │  • serviceType│     │
 * │  │  • balance    │         │  • quantity   │         │  • costPer1k  │     │
 * │  │  • status     │         │  • status     │         │  • geoProfiles│     │
 * │  └───────────────┘         └───────────────┘         └───────────────┘     │
 * │         │                         │                                         │
 * │         │ 1:N                     │ 1:N                                     │
 * │         ▼                         ▼                                         │
 * │  ┌───────────────┐         ┌───────────────┐                               │
 * │  │BalanceTxEntity│         │ProxyMetrics   │                               │
 * │  │               │         │   Entity      │                               │
 * │  │  • amount     │         │  • successRate│                               │
 * │  │  • type       │         │  • latencyP95 │                               │
 * │  │  • timestamp  │         │  • banRate    │                               │
 * │  └───────────────┘         └───────────────┘                               │
 * │                                   ▲                                         │
 * │                                   │ 1:1                                     │
 * │  ┌───────────────┐         ┌───────────────┐         ┌───────────────┐     │
 * │  │DeviceNode     │         │ProxyNode      │         │TorCircuit     │     │
 * │  │   Entity      │         │   Entity      │         │   Entity      │     │
 * │  │               │         │               │         │               │     │
 * │  │  • deviceId   │         │  • host       │         │  • circuitId  │     │
 * │  │  • userAgent  │         │  • provider   │         │  • exitIp     │     │
 * │  │  • platform   │         │  • tier       │         │  • exitCountry│     │
 * │  │  • canvasHash │         │  • geoProfile │         │  • isStable   │     │
 * │  └───────────────┘         └───────────────┘         └───────────────┘     │
 * │                                                                             │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Entities</h2>
 * <ul>
 *   <li>{@link com.goodfellaz17.domain.model.generated.UserEntity} - Platform users with tiered pricing</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.OrderEntity} - Core delivery order aggregate root</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ServiceEntity} - Spotify engagement service catalog</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.BalanceTransactionEntity} - Financial ledger</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ProxyNodeEntity} - Distributed proxy pool nodes</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ProxyMetricsEntity} - Real-time proxy health metrics</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.DeviceNodeEntity} - Device fingerprint profiles</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.TorCircuitEntity} - Tor circuit sessions</li>
 * </ul>
 * 
 * <h2>Enums</h2>
 * <ul>
 *   <li>{@link com.goodfellaz17.domain.model.generated.UserTier} - CONSUMER, RESELLER, AGENCY</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.UserStatus} - PENDING, ACTIVE, SUSPENDED, BANNED</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.OrderStatus} - Full order lifecycle states</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.GeoProfile} - Geographic targeting regions</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ServiceType} - Spotify engagement types</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.TransactionType} - CREDIT, DEBIT, REFUND, BONUS, ADJUSTMENT</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ProxyProvider} - Proxy vendor providers</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ProxyTier} - DATACENTER, RESIDENTIAL, MOBILE, ISP, PREMIUM</li>
 *   <li>{@link com.goodfellaz17.domain.model.generated.ProxyStatus} - ACTIVE, DEGRADED, MAINTENANCE, EXHAUSTED, OFFLINE</li>
 * </ul>
 * 
 * <h2>Generation Details</h2>
 * <ul>
 *   <li><b>Grammar:</b> DomainModel.mc4 v1.0.0</li>
 *   <li><b>Instance:</b> Goodfellaz17.dm</li>
 *   <li><b>Generator:</b> MontiCore DomainModel Generator v1.0.0</li>
 *   <li><b>Target:</b> Spring Boot 3.5.0 + R2DBC + PostgreSQL</li>
 * </ul>
 * 
 * @author MontiCore Generator
 * @version 1.0.0
 * @since 2024-01-01
 * @generated
 */
@javax.annotation.processing.Generated(
    value = "MontiCore DomainModel Generator",
    date = "2024-01-01",
    comments = "Generated from DomainModel.mc4 / Goodfellaz17.dm"
)
package com.goodfellaz17.domain.model.generated;
