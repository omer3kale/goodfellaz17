/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * 
 * This package contains all generated Spring Data R2DBC repositories for the
 * goodfellaz17 domain model. Each repository provides reactive CRUD operations
 * and custom queries for its corresponding entity.
 * 
 * <h2>Repository Overview</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                         REPOSITORY LAYER                                    │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │                                                                             │
 * │  ┌─────────────────────────────┐    ┌─────────────────────────────┐        │
 * │  │ GeneratedUserRepository     │    │ GeneratedOrderRepository    │        │
 * │  ├─────────────────────────────┤    ├─────────────────────────────┤        │
 * │  │ • findByEmail               │    │ • findByUserId              │        │
 * │  │ • findByApiKey              │    │ • findByStatus              │        │
 * │  │ • findActiveByTier          │    │ • updateProgress            │        │
 * │  │ • updateBalance             │    │ • updateStatus              │        │
 * │  │ • regenerateApiKey          │    │ • sumRevenueInPeriod        │        │
 * │  └─────────────────────────────┘    └─────────────────────────────┘        │
 * │                                                                             │
 * │  ┌─────────────────────────────┐    ┌─────────────────────────────┐        │
 * │  │ GeneratedServiceRepository  │    │ GeneratedBalanceTxRepository│        │
 * │  ├─────────────────────────────┤    ├─────────────────────────────┤        │
 * │  │ • findActiveByType          │    │ • findByUserId              │        │
 * │  │ • findActiveBelowPrice      │    │ • findByUserIdInRange       │        │
 * │  │ • getPriceForTier           │    │ • sumCreditsForUser         │        │
 * │  │ • updatePricing             │    │ • dailyRevenueInPeriod      │        │
 * │  └─────────────────────────────┘    └─────────────────────────────┘        │
 * │                                                                             │
 * │  ┌─────────────────────────────┐    ┌─────────────────────────────┐        │
 * │  │ GeneratedProxyNodeRepository│    │ GeneratedProxyMetricsRepo   │        │
 * │  ├─────────────────────────────┤    ├─────────────────────────────┤        │
 * │  │ • findAvailableByGeo        │    │ • findHealthyMetrics        │        │
 * │  │ • findHealthyByTier         │    │ • recordSuccess             │        │
 * │  │ • selectWithMetrics         │    │ • recordFailure             │        │
 * │  │ • incrementActiveConns      │    │ • updateLatencyPercentiles  │        │
 * │  └─────────────────────────────┘    └─────────────────────────────┘        │
 * │                                                                             │
 * │  ┌─────────────────────────────┐    ┌─────────────────────────────┐        │
 * │  │ GeneratedDeviceNodeRepo     │    │ GeneratedTorCircuitRepo     │        │
 * │  ├─────────────────────────────┤    ├─────────────────────────────┤        │
 * │  │ • findRandomHealthyDevices  │    │ • findActiveByCountry       │        │
 * │  │ • findByLanguagePrefix      │    │ • findStableCircuits        │        │
 * │  │ • recordSessionResult       │    │ • rotateCircuit             │        │
 * │  │ • disableHighBanRateDevices │    │ • closeExpiredCircuits      │        │
 * │  └─────────────────────────────┘    └─────────────────────────────┘        │
 * │                                                                             │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Reactive:</b> All repositories extend R2dbcRepository for non-blocking I/O</li>
 *   <li><b>Custom Queries:</b> @Query annotations for complex operations</li>
 *   <li><b>Type Safe:</b> Strongly typed parameters and return values</li>
 *   <li><b>Pagination:</b> Built-in support for paginated queries</li>
 *   <li><b>Aggregations:</b> Revenue, stats, and analytics queries</li>
 * </ul>
 * 
 * @author MontiCore Generator
 * @version 1.0.0
 * @generated
 */
@javax.annotation.processing.Generated(
    value = "MontiCore DomainModel Generator",
    date = "2024-01-01",
    comments = "Generated from DomainModel.mc4 / Goodfellaz17.dm"
)
package com.goodfellaz17.infrastructure.persistence.generated;
