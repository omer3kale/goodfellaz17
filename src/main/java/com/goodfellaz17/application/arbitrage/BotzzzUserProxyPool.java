package com.goodfellaz17.application.arbitrage;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import com.goodfellaz17.infrastructure.user.UserStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Application Service - botzzz773.pro User Proxy Pool.
 * 
 * YOUR GENIUS: Users ARE the infrastructure.
 * 
 * Traditional: $50/mo BrightData proxies + $1000 accounts
 * Arbitrage:   $0 (users provide both)
 * 
 * Revenue Split:
 * - You: 70%
 * - User: 30% commission per task
 * 
 * Scale: 1000+ botzzz773.pro users = infinite capacity
 * 
 * PRODUCTION MODE:
 * - Users register via WebSocket connection
 * - No mock users, no external dependencies
 * - Real users connect from botzzz773.pro panel
 * 
 * DEV MODE:
 * - bootstrapMockUsers() creates fake users for testing
 */
@Service
public class BotzzzUserProxyPool {

    private static final Logger log = LoggerFactory.getLogger(BotzzzUserProxyPool.class);

    private final Queue<UserProxy> availableUsers = new ConcurrentLinkedQueue<>();
    private final Map<String, UserProxy> allUsers = new ConcurrentHashMap<>();
    private final Map<UUID, TaskAssignment> pendingTasks = new ConcurrentHashMap<>();
    
    private final SimpMessagingTemplate websocket;
    private final Environment environment;

    @Value("${arbitrage.commission-rate:0.30}")
    private double commissionRate;

    @Value("${arbitrage.task-timeout-seconds:300}")
    private int taskTimeoutSeconds;

    public BotzzzUserProxyPool(
            SimpMessagingTemplate websocket,
            Environment environment) {
        this.websocket = websocket;
        this.environment = environment;
    }

    /**
     * Bootstrap user farm.
     * 
     * PRODUCTION: Starts empty, users register via WebSocket
     * DEV: Creates mock users for testing
     */
    @PostConstruct
    public void bootstrapUserFarm() {
        if (isDevProfile()) {
            bootstrapMockUsers();
            return;
        }
        
        // Production: Start with empty pool
        // Real users connect via WebSocket from botzzz773.pro panel
        log.info("Production mode: User pool initialized empty, waiting for WebSocket connections");
    }

    /**
     * Development ONLY: Mock users for testing.
     * 
     * Only runs when 'dev' profile is active.
     */
    private void bootstrapMockUsers() {
        if (!isDevProfile()) {
            log.warn("Attempted to bootstrap mock users in production - blocked");
            return;
        }
        
        List<GeoTarget> geos = List.of(GeoTarget.USA, GeoTarget.EU, GeoTarget.WORLDWIDE);
        
        for (int i = 0; i < 100; i++) {
            UserProxy user = UserProxy.builder()
                    .userId("mock-user-" + i)
                    .ipAddress("192.168.1." + (i % 255))
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")
                    .geoTarget(geos.get(i % 3))
                    .hasSpotifyPremium(true)
                    .build();
            
            allUsers.put(user.getId(), user);
            availableUsers.add(user);
        }
        
        log.info("DEV MODE: Mock user farm created with {} users", availableUsers.size());
    }
    
    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private GeoTarget parseGeoTarget(String country) {
        return switch (country.toUpperCase()) {
            case "US", "USA", "UNITED STATES" -> GeoTarget.USA;
            case "GB", "DE", "FR", "IT", "ES", "NL", "BE", "AT", "CH" -> GeoTarget.EU;
            default -> GeoTarget.WORLDWIDE;
        };
    }

    // === User Selection ===

    /**
     * Get next healthy user matching geo target.
     * 
     * Round-robin with geo filtering.
     */
    public Optional<UserProxy> nextHealthyUser(GeoTarget geo) {
        for (int i = 0; i < availableUsers.size(); i++) {
            UserProxy user = availableUsers.poll();
            if (user == null) break;
            
            if (user.isHealthy() && user.matchesGeo(geo)) {
                return Optional.of(user);
            }
            
            // Return to pool if still healthy
            if (user.isHealthy()) {
                availableUsers.add(user);
            }
        }
        
        return Optional.empty();
    }

    /**
     * Send task to user via WebSocket.
     */
    public void sendTask(UserProxy user, TaskAssignment task) {
        user.assignTask();
        pendingTasks.put(task.taskId(), task);
        
        // Send to user's browser via WebSocket
        websocket.convertAndSendToUser(
                user.getUserId(),
                "/queue/tasks",
                task
        );
        
        log.info("Task sent: userId={}, taskId={}, commission={}", 
                user.getUserId(), task.taskId(), task.commission());
    }

    /**
     * Handle task completion from user.
     */
    public void completeTask(UUID taskId, String userId, int plays) {
        TaskAssignment task = pendingTasks.remove(taskId);
        UserProxy user = allUsers.get(userId);
        
        if (task != null && user != null) {
            user.completeTask(task.commission().doubleValue());
            availableUsers.add(user);
            
            log.info("Task completed: userId={}, plays={}, commission={}", 
                    userId, plays, task.commission());
        }
    }

    /**
     * Register new user (WebSocket connect).
     */
    public void registerUser(String sessionId, String userId, String ipAddress, 
                             String userAgent, String country) {
        GeoTarget geo = parseGeoTarget(country);
        
        UserProxy user = UserProxy.builder()
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .geoTarget(geo)
                .hasSpotifyPremium(true)
                .build();
        
        allUsers.put(user.getId(), user);
        availableUsers.add(user);
        
        log.info("User registered: userId={}, geo={}", userId, geo);
    }

    /**
     * Handle user disconnect.
     */
    public void disconnectUser(String userId) {
        allUsers.values().stream()
                .filter(u -> u.getUserId().equals(userId))
                .forEach(UserProxy::disconnect);
        
        log.info("User disconnected: userId={}", userId);
    }

    // === Health Check ===

    /**
     * Cleanup stale users (5 min timeout).
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupStaleUsers() {
        int removed = 0;
        Iterator<UserProxy> it = availableUsers.iterator();
        
        while (it.hasNext()) {
            UserProxy user = it.next();
            if (!user.isHealthy()) {
                it.remove();
                user.disconnect();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.debug("Cleaned up {} stale users", removed);
        }
    }

    // === Metrics ===

    public int getActiveUserCount() {
        return (int) allUsers.values().stream()
                .filter(u -> u.getStatus() != UserStatus.OFFLINE)
                .count();
    }

    public int getAvailableUserCount() {
        return availableUsers.size();
    }

    public int getBusyUserCount() {
        return (int) allUsers.values().stream()
                .filter(u -> u.getStatus() == UserStatus.BUSY)
                .count();
    }

    public Map<GeoTarget, Long> getUsersByGeo() {
        Map<GeoTarget, Long> byGeo = new HashMap<>();
        for (GeoTarget geo : GeoTarget.values()) {
            long count = allUsers.values().stream()
                    .filter(u -> u.getGeoTarget() == geo)
                    .filter(u -> u.getStatus() != UserStatus.OFFLINE)
                    .count();
            byGeo.put(geo, count);
        }
        return byGeo;
    }
}
