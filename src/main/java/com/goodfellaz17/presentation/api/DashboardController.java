package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.application.service.RoutingEngine;
import com.goodfellaz17.domain.port.ProxyStrategy;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dashboard Controller - Revenue metrics and analytics.
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
    
    // Revenue tracking (production: persist to DB)
    private final AtomicReference<BigDecimal> totalRevenue = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> todayRevenue = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicInteger totalOrders = new AtomicInteger(0);
    private final AtomicInteger todayOrders = new AtomicInteger(0);
    private final AtomicInteger totalPlaysDelivered = new AtomicInteger(0);
    private LocalDate lastResetDate = LocalDate.now();

    public DashboardController(RoutingEngine routingEngine, BotzzzUserProxyPool userProxyPool) {
        this.routingEngine = routingEngine;
        this.userProxyPool = userProxyPool;
    }

    /**
     * Main dashboard - all metrics.
     */
    @GetMapping
    public DashboardResponse dashboard() {
        resetDailyCountersIfNeeded();
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
        if (!today.equals(lastResetDate)) {
            todayRevenue.set(BigDecimal.ZERO);
            todayOrders.set(0);
            lastResetDate = today;
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
