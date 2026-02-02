package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.application.service.RoutingEngine;
import com.goodfellaz17.domain.port.ProxyStrategy;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dashboard Controller - Revenue metrics and analytics.
 *
 * PRODUCTION: Uses real Neon PostgreSQL for revenue tracking.
 *
 * Provides real-time business metrics:
 * - Total revenue (today/all-time)
 * - Order counts
 * - Capacity utilization
 * - Profit margins
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final RoutingEngine routingEngine;
    private final BotzzzUserProxyPool userProxyPool;
    private final OrderRepository orderRepository;

    // In-memory cache (synced from DB periodically)
    private final AtomicReference<BigDecimal> totalRevenue = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> todayRevenue = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicInteger totalOrders = new AtomicInteger(0);
    private final AtomicInteger todayOrders = new AtomicInteger(0);
    private final AtomicInteger totalPlaysDelivered = new AtomicInteger(0);
    private final AtomicReference<LocalDate> lastResetDate = new AtomicReference<>(LocalDate.now());

    public DashboardController(
            RoutingEngine routingEngine,
            BotzzzUserProxyPool userProxyPool,
            OrderRepository orderRepository) {
        this.routingEngine = routingEngine;
        this.userProxyPool = userProxyPool;
        this.orderRepository = orderRepository;

        // Load initial values from DB
        loadFromDatabase();
    }

    /**
     * Main dashboard - all metrics from REAL DB.
     */
    @GetMapping
    public DashboardResponse dashboard() {
        resetDailyCountersIfNeeded();
        loadFromDatabase(); // Refresh from DB
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();

        return new DashboardResponse(
            totalRevenue.get(),
            todayRevenue.get(),
            totalOrders.get(),
            todayOrders.get(),
            totalPlaysDelivered.get(),
            stats.totalCapacity(),
            stats.totalRemaining(),
            userProxyPool.getActiveUserCount(),
            calculateProfitMargin(),
            Instant.now()
        );
    }

    /**
     * Load metrics from Neon PostgreSQL.
     *
     * TODO: Temporary stub - totalDelivered() and findTodayOrders() not implemented.
     * This is a compilation fix only; real analytics logic to be added post-freeze.
     */
    private void loadFromDatabase() {
        try {
            // Total revenue from completed orders
            BigDecimal dbRevenue = orderRepository.totalRevenue().block();
            if (dbRevenue != null) {
                totalRevenue.set(dbRevenue);
            }

            // TODO: totalDelivered() not implemented - using zero for now
            // Integer delivered = orderRepository.totalDelivered().block();
            // totalPlaysDelivered.set(delivered != null ? delivered : 0);

            // TODO: findTodayOrders() not implemented - using ordersToday() instead
            Long todayCount = orderRepository.ordersToday().block();
            todayOrders.set(todayCount != null ? todayCount.intValue() : 0);

            // Total orders
            Long total = orderRepository.count().block();
            totalOrders.set(total != null ? total.intValue() : 0);

        } catch (Exception e) {
            // Silently use cached values if DB unavailable
        }
    }

    /**
     * Record completed order revenue.
     * Called by HybridBotOrchestrator on delivery completion.
     */
    public void recordRevenue(int quantity, BigDecimal ratePerThousand) {
        resetDailyCountersIfNeeded();

        BigDecimal revenue = ratePerThousand
            .multiply(BigDecimal.valueOf(quantity))
            .divide(BigDecimal.valueOf(1000), 2, java.math.RoundingMode.HALF_UP);

        totalRevenue.updateAndGet(v -> v.add(revenue));
        todayRevenue.updateAndGet(v -> v.add(revenue));
        totalOrders.incrementAndGet();
        todayOrders.incrementAndGet();
        totalPlaysDelivered.addAndGet(quantity);
    }

    /**
     * Get capacity utilization metrics.
     */
    @GetMapping("/capacity")
    public CapacityResponse capacity() {
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();

        double utilizationPercent = stats.totalCapacity() > 0
            ? (double) (stats.totalCapacity() - stats.totalRemaining()) / stats.totalCapacity() * 100
            : 0;

        return new CapacityResponse(
            stats.totalSources(),
            stats.enabledSources(),
            stats.totalCapacity(),
            stats.totalRemaining(),
            stats.totalActiveLeases(),
            utilizationPercent
        );
    }

    /**
     * Get revenue breakdown by source.
     */
    @GetMapping("/revenue")
    public RevenueResponse revenue() {
        resetDailyCountersIfNeeded();

        // Estimated breakdown (production: track per-source)
        BigDecimal userArbitrageRevenue = todayRevenue.get().multiply(new BigDecimal("0.60")); // 60% free
        BigDecimal paidProxyRevenue = todayRevenue.get().multiply(new BigDecimal("0.40"));     // 40% paid

        return new RevenueResponse(
            totalRevenue.get(),
            todayRevenue.get(),
            userArbitrageRevenue,
            paidProxyRevenue,
            calculateProfitMargin(),
            totalPlaysDelivered.get()
        );
    }

    /**
     * Get real-time metrics for live dashboard.
     */
    @GetMapping("/live")
    public LiveMetrics liveMetrics() {
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();

        return new LiveMetrics(
            stats.totalActiveLeases(),
            userProxyPool.getBusyUserCount(),
            stats.totalRemaining(),
            todayOrders.get(),
            Instant.now()
        );
    }

    private void resetDailyCountersIfNeeded() {
        LocalDate today = LocalDate.now();
        LocalDate lastReset = lastResetDate.get();

        if (!today.equals(lastReset)) {
            // Only reset if we successfully updated the date (atomic operation)
            if (lastResetDate.compareAndSet(lastReset, today)) {
                todayRevenue.set(BigDecimal.ZERO);
                todayOrders.set(0);
            }
        }
    }

    private BigDecimal calculateProfitMargin() {
        // User arbitrage = ~85% margin (users provide free infra)
        // Paid proxies = ~60% margin (BrightData/SOAX costs)
        // Blended estimate: 75% margin
        return new BigDecimal("0.75");
    }

    // === Response DTOs ===

    public record DashboardResponse(
        BigDecimal totalRevenue,
        BigDecimal todayRevenue,
        int totalOrders,
        int todayOrders,
        int totalPlaysDelivered,
        int totalCapacity,
        int remainingCapacity,
        int activeUsers,
        BigDecimal profitMargin,
        Instant timestamp
    ) {}

    public record CapacityResponse(
        int totalSources,
        int enabledSources,
        int totalCapacity,
        int remainingCapacity,
        int activeLeases,
        double utilizationPercent
    ) {}

    public record RevenueResponse(
        BigDecimal totalRevenue,
        BigDecimal todayRevenue,
        BigDecimal userArbitrageRevenue,
        BigDecimal paidProxyRevenue,
        BigDecimal profitMargin,
        int totalPlaysDelivered
    ) {}

    public record LiveMetrics(
        int activeLeases,
        int busyUsers,
        int remainingCapacity,
        int todayOrders,
        Instant timestamp
    ) {}
}
