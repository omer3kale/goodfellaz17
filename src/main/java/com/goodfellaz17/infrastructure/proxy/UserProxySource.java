package com.goodfellaz17.infrastructure.proxy;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Commission rate for users (30%)
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.30");
    
    public UserProxySource(BotzzzUserProxyPool userPool) {
        this.userPool = userPool;
    }
    
    @Override
    public String getName() {
        return "USER_ARBITRAGE";
    }
    
    @Override
    public ServicePriority getPriority() {
        // Highest priority - zero cost
        return ServicePriority.ULTRA;
    }
    
    @Override
    public boolean supportsGeo(GeoTarget geo) {
        Map<GeoTarget, Long> usersByGeo = userPool.getUsersByGeo();
        Long count = usersByGeo.getOrDefault(geo, 0L);
        return count > 0;
    }
    
    @Override
    public int getAvailableCapacity() {
        return userPool.getAvailableUserCount();
    }
    
    @Override
    public Duration getTypicalLatency() {
        // User execution via WebSocket = variable latency
        return Duration.ofSeconds(30);
    }
    
    @Override
    public boolean isHealthy() {
        // Healthy if we have at least 5 available users
        return userPool.getAvailableUserCount() >= 5;
    }
    
    @Override
    public ProxyLease acquire(GeoTarget geo) throws NoCapacityException {
        Optional<UserProxy> userOpt = userPool.nextHealthyUser(geo);
        
        if (userOpt.isEmpty()) {
            throw new NoCapacityException(
                    "No available users for geo: " + geo,
                    getName(),
                    0
            );
        }
        
        UserProxy user = userOpt.get();
        String leaseId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        
        // Create proxy representation for the user
        Proxy proxy = Proxy.builder()
                .id(user.getId())
                .host(user.getIpAddress())
                .port(0)  // No port - user executes directly
                .country(geo.name())
                .type(Proxy.ProxyType.HTTP)
                .provider("USER_ARBITRAGE")
                .build();
        
        ProxyLease lease = new ProxyLease(
                leaseId,
                proxy,
                now,
                now.plus(Duration.ofMinutes(5)),
                getName()
        );
        
        // Track lease with user reference
        activeLeases.put(leaseId, new UserLease(lease, user));
        
        log.info("User lease acquired: leaseId={}, userId={}, geo={}", 
                leaseId, user.getUserId(), geo);
        
        return lease;
    }
    
    @Override
    public void release(ProxyLease lease) {
        UserLease userLease = activeLeases.remove(lease.leaseId());
        
        if (userLease != null) {
            // User goes back to available pool automatically
            // after task completion via BotzzzUserProxyPool.completeTask()
            log.debug("User lease released: leaseId={}", lease.leaseId());
        }
    }
    
    /**
     * Execute a task through a user.
     * 
     * This sends the task to the user's browser via WebSocket.
     */
    public void executeTask(ProxyLease lease, String trackUri, int plays, BigDecimal pricePerPlay) {
        UserLease userLease = activeLeases.get(lease.leaseId());
        
        if (userLease == null) {
            log.warn("No active lease found: {}", lease.leaseId());
            return;
        }
        
        // Calculate user commission (30% of price)
        BigDecimal commission = pricePerPlay.multiply(COMMISSION_RATE)
                .multiply(BigDecimal.valueOf(plays));
        
        TaskAssignment task = new TaskAssignment(
                UUID.randomUUID(),
                trackUri,
                plays,
                commission,
                Instant.now().plusSeconds(300)  // 5 min deadline
        );
        
        userPool.sendTask(userLease.user(), task);
        
        log.info("Task dispatched to user: userId={}, plays={}, commission={}", 
                userLease.user().getUserId(), plays, commission);
    }
    
    /**
     * Get statistics about user pool.
     */
    public UserPoolStats getStats() {
        return new UserPoolStats(
                userPool.getActiveUserCount(),
                userPool.getAvailableUserCount(),
                userPool.getBusyUserCount(),
                activeLeases.size(),
                userPool.getUsersByGeo()
        );
    }
    
    // Internal record linking lease to user
    private record UserLease(ProxyLease lease, UserProxy user) {}
    
    // Stats record
    public record UserPoolStats(
            int activeUsers,
            int availableUsers,
            int busyUsers,
            int activeLeases,
            Map<GeoTarget, Long> usersByGeo
    ) {}
}
