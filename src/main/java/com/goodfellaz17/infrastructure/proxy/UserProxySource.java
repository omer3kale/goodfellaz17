package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.infrastructure.user.UserProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ProxySource adapter for User Arbitrage Network.
 *
 * THE GENIUS: Users ARE the proxy infrastructure.
 *
 * - Zero proxy costs (users provide residential IPs)
 * - Zero account costs (users have Spotify Premium)
 * - 30% commission to users = pure arbitrage profit
 *
 * This adapts BotzzzUserProxyPool to the ProxySource interface
 * for seamless integration with hybrid routing.
 */
@Component
@Profile("!dev")  // Real user pool in production only
public class UserProxySource implements ProxySource {

    private static final Logger log = LoggerFactory.getLogger(UserProxySource.class);

    private final BotzzzUserProxyPool userPool;
    private final Map<String, UserLease> activeLeases = new ConcurrentHashMap<>();

    // Capacity tracking
    private final AtomicInteger usedToday = new AtomicInteger(0);
    private final AtomicLong lastAcquireTime = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);

    // Configuration (could be externalized)
    private static final int CAPACITY_PER_DAY = 100000;  // 100k via user network
    private static final double COST_PER_1K = 0.00;      // FREE - users pay themselves
    private static final double RISK_LEVEL = 0.2;        // Low risk - real residential IPs

    public UserProxySource(BotzzzUserProxyPool userPool) {
        this.userPool = userPool;
    }

    @Override
    public String getName() {
        return "user";
    }

    @Override
    public String getDisplayName() {
        return "User Arbitrage Network";
    }

    @Override
    public boolean isEnabled() {
        // Enabled if we have at least 1 available user
        return userPool != null && userPool.getAvailableUserCount() > 0;
    }

    @Override
    public boolean supportsGeo(String country) {
        if (country == null || "GLOBAL".equalsIgnoreCase(country)) {
            return true;
        }
        // Check if we have users in this geo
        try {
            GeoTarget geo = GeoTarget.valueOf(country.toUpperCase());
            Map<GeoTarget, Long> usersByGeo = userPool.getUsersByGeo();
            Long count = usersByGeo.getOrDefault(geo, 0L);
            return count > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean supportsProfile(RoutingProfile profile) {
        // User network is good for everything except ultra-high-volume
        // (we don't want to overwhelm users)
        if (profile.priority() == ServicePriority.HIGH_VOLUME) {
            return false;
        }
        return profile.allowsRisk(RISK_LEVEL);
    }

    @Override
    public int getEstimatedCapacityPerDay() {
        return CAPACITY_PER_DAY;
    }

    @Override
    public int getRemainingCapacity() {
        return Math.max(0, CAPACITY_PER_DAY - usedToday.get());
    }

    @Override
    public double getCostPer1k() {
        return COST_PER_1K;  // FREE!
    }

    @Override
    public double getRiskLevel() {
        return RISK_LEVEL;
    }

    @Override
    public boolean isPremium() {
        return true;  // Real residential IPs = premium quality
    }

    @Override
    public ProxyLease acquire(OrderContext ctx) throws NoCapacityException {
        // Check capacity
        if (getRemainingCapacity() <= 0) {
            throw new NoCapacityException(ctx.orderId(), ctx.serviceId(), getName());
        }

        // Find available user for this geo
        GeoTarget geo = parseGeo(ctx.targetCountry());
        Optional<UserProxy> userOpt = userPool.nextHealthyUser(geo);

        if (userOpt.isEmpty()) {
            throw new NoCapacityException(ctx.orderId(), ctx.serviceId(), getName());
        }

        UserProxy user = userOpt.get();

        // Create lease using user's IP as the "proxy"
        ProxyLease lease = ProxyLease.create(
            getName(),
            user.getIpAddress(),
            0,  // No port - user executes directly
            ProxyLease.ProxyType.HTTP,
            ctx.targetCountry() != null ? ctx.targetCountry() : "GLOBAL",
            RISK_LEVEL,
            300,  // 5 minute TTL
            Map.of(
                "user_id", user.getUserId(),
                "order_id", ctx.orderId() != null ? ctx.orderId() : "",
                "service", ctx.serviceName() != null ? ctx.serviceName() : "",
                "source", "user_arbitrage"
            )
        );

        // Track lease
        activeLeases.put(lease.leaseId(), new UserLease(lease, user));
        usedToday.incrementAndGet();
        lastAcquireTime.set(System.currentTimeMillis());

        log.info("User lease acquired: leaseId={}, userId={}, geo={}",
                lease.leaseId(), user.getUserId(), geo);

        return lease;
    }

    @Override
    public void release(ProxyLease lease) {
        UserLease userLease = activeLeases.remove(lease.leaseId());

        if (userLease != null) {
            // User goes back to available pool automatically
            log.debug("User lease released: leaseId={}", lease.leaseId());
            successCount.incrementAndGet();
        }
    }

    /**
     * Get success rate for monitoring/observability.
     */
    public double getSuccessRate() {
        long total = successCount.get() + failCount.get();
        return total > 0 ? (successCount.get() * 100.0) / total : 0.0;
    }

    @Override
    public SourceStats getStats() {
        long successTotal = successCount.get();
        long failTotal = failCount.get();
        double successRate = (successTotal + failTotal) > 0
            ? (double) successTotal / (successTotal + failTotal)
            : 1.0;

        return new SourceStats(
            getName(),
            CAPACITY_PER_DAY,
            usedToday.get(),
            getRemainingCapacity(),
            activeLeases.size(),
            successRate,
            lastAcquireTime.get()
        );
    }

    // Helper to parse geo string to GeoTarget
    private GeoTarget parseGeo(String country) {
        if (country == null || "GLOBAL".equalsIgnoreCase(country)) {
            return GeoTarget.WORLDWIDE;
        }
        try {
            return GeoTarget.valueOf(country.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GeoTarget.WORLDWIDE;
        }
    }

    // Internal record linking lease to user
    private record UserLease(ProxyLease lease, UserProxy user) {}
}
