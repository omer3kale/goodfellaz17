package com.goodfellaz17.application.arbitrage;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub implementation of BotzzzUserProxyPool.
 *
 * The full STOMP-based implementation is backed up in .backup-generated/
 * This stub provides minimal functionality for the app to compile and run.
 *
 * TODO Phase 3: Implement reactive WebSocket version when WebSocket support is needed.
 *
 * Implementation path:
 * 1. Add spring-boot-starter-websocket dependency (reactive variant)
 * 2. Create WebSocketConfig with STOMP endpoint /ws/user-proxy
 * 3. Implement UserProxyWebSocketHandler for bidirectional messaging
 * 4. Replace stub methods with real WebSocket pub/sub
 * 5. Add connection tracking with heartbeat mechanism
 * 6. Migrate user proxy registration to WebSocket handshake
 *
 * Current stub behavior:
 * - nextHealthyUser() returns empty (no user proxies available)
 * - sendTask() logs only (no actual delivery)
 * - getConnectedUserCount() tracks in-memory registrations
 *
 * @see .backup-generated/BotzzzUserProxyPool.java for full STOMP implementation
 */
@Component
public class BotzzzUserProxyPool {

    private static final Logger log = LoggerFactory.getLogger(BotzzzUserProxyPool.class);

    private final Map<String, UserProxyInfo> userProxies = new ConcurrentHashMap<>();
    private final Map<String, UserProxy> connectedUsers = new ConcurrentHashMap<>();

    /**
     * Get a proxy from a user's pool (stub - returns empty).
     */
    public Mono<Optional<InetSocketAddress>> getProxyFromUser(String userId) {
        return Mono.just(Optional.empty());
    }

    /**
     * Get next healthy user for a geo target (stub - returns empty).
     */
    public Optional<UserProxy> nextHealthyUser(GeoTarget geoTarget) {
        log.debug("nextHealthyUser called for geo={} (stub mode - returning empty)", geoTarget);
        return connectedUsers.values().stream()
                .filter(UserProxy::isHealthy)
                .filter(u -> u.matchesGeo(geoTarget))
                .findFirst();
    }

    /**
     * Send task to user (stub - logs only).
     */
    public void sendTask(UserProxy user, TaskAssignment task) {
        log.info("sendTask called for user={} task={} (stub mode - not sending)",
                user.getUserId(), task.taskId());
    }

    /**
     * Complete a task (stub - logs only).
     */
    public void completeTask(UUID taskId, String userId, int plays) {
        log.info("completeTask called: taskId={} userId={} plays={} (stub mode)",
                taskId, userId, plays);
    }

    /**
     * Register a user's proxy.
     */
    public void registerUserProxy(String userId, String ip, int port) {
        userProxies.put(userId, new UserProxyInfo(userId, ip, port));
    }

    /**
     * Get all connected users count.
     */
    public int getConnectedUserCount() {
        return connectedUsers.size();
    }

    /**
     * Get available user count (healthy and not busy).
     */
    public int getAvailableUserCount() {
        return (int) connectedUsers.values().stream()
                .filter(UserProxy::isHealthy)
                .count();
    }

    /**
     * Get active user count (connected recently).
     */
    public int getActiveUserCount() {
        return connectedUsers.size();
    }

    /**
     * Get busy user count (currently executing tasks).
     */
    public int getBusyUserCount() {
        return 0; // Stub - no tracking
    }

    /**
     * Get users grouped by geo.
     */
    public Map<GeoTarget, Long> getUsersByGeo() {
        Map<GeoTarget, Long> result = new EnumMap<>(GeoTarget.class);
        for (GeoTarget geo : GeoTarget.values()) {
            result.put(geo, 0L);
        }
        return result;
    }

    /**
     * Get all active proxies count.
     */
    public int getActiveProxyCount() {
        return userProxies.size();
    }

    /**
     * Get stats map.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("connectedUsers", getConnectedUserCount());
        stats.put("activeProxies", getActiveProxyCount());
        stats.put("availableUsers", getAvailableUserCount());
        stats.put("busyUsers", getBusyUserCount());
        stats.put("status", "STUB_MODE");
        return stats;
    }

    /**
     * Check if pool has available proxies.
     */
    public boolean hasAvailableProxies() {
        return !userProxies.isEmpty();
    }

    /**
     * Simple user proxy info holder.
     */
    public record UserProxyInfo(String userId, String ip, int port) {}
}
