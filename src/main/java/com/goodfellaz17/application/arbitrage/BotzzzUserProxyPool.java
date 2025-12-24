package com.goodfellaz17.application.arbitrage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import com.goodfellaz17.infrastructure.user.UserStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.StreamSupport;

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
 */
@Service
public class BotzzzUserProxyPool {

    private static final Logger log = LoggerFactory.getLogger(BotzzzUserProxyPool.class);

    private final Queue<UserProxy> availableUsers = new ConcurrentLinkedQueue<>();
    private final Map<String, UserProxy> allUsers = new ConcurrentHashMap<>();
    private final Map<UUID, TaskAssignment> pendingTasks = new ConcurrentHashMap<>();
    
    private final WebClient supabaseClient;
    private final SimpMessagingTemplate websocket;
    private final ObjectMapper objectMapper;

    @Value("${arbitrage.commission-rate:0.30}")
    private double commissionRate;

    @Value("${arbitrage.task-timeout-seconds:300}")
    private int taskTimeoutSeconds;

    public BotzzzUserProxyPool(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.key:}") String supabaseKey,
            SimpMessagingTemplate websocket) {
        
        this.websocket = websocket;
        this.objectMapper = new ObjectMapper();
        
        if (supabaseUrl != null && !supabaseUrl.isEmpty()) {
            this.supabaseClient = WebClient.builder()
                    .baseUrl(supabaseUrl + "/rest/v1")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                    .defaultHeader("apikey", supabaseKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.supabaseClient = null;
            log.warn("Supabase not configured - using mock user pool");
        }
    }

    /**
     * Bootstrap user farm from botzzz773.pro database.
     * 
     * Your 1000+ panel users = FREE proxy farm.
     */
    @PostConstruct
    public void bootstrapUserFarm() {
        if (supabaseClient == null) {
            bootstrapMockUsers();
            return;
        }

        try {
            String response = supabaseClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users")
                            .queryParam("status", "eq.active")
                            .queryParam("has_spotify", "eq.true")
                            .queryParam("select", "id,ip_address,user_agent,country")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode users = objectMapper.readTree(response);
            StreamSupport.stream(users.spliterator(), false)
                    .forEach(this::registerUserFromJson);

            log.info("User farm bootstrapped: {} users available", availableUsers.size());

        } catch (Exception e) {
            log.error("Failed to bootstrap user farm: {}", e.getMessage());
            bootstrapMockUsers();
        }
    }

    /**
     * Development: Mock users for testing
     */
    private void bootstrapMockUsers() {
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
        
        log.info("Mock user farm created: {} users", availableUsers.size());
    }

    private void registerUserFromJson(JsonNode node) {
        try {
            String country = node.has("country") ? node.get("country").asText() : "US";
            GeoTarget geo = parseGeoTarget(country);
            
            UserProxy user = UserProxy.builder()
                    .userId(node.get("id").asText())
                    .ipAddress(node.get("ip_address").asText())
                    .userAgent(node.has("user_agent") ? node.get("user_agent").asText() 
                            : "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")
                    .geoTarget(geo)
                    .hasSpotifyPremium(true)
                    .build();
            
            allUsers.put(user.getId(), user);
            availableUsers.add(user);
            
        } catch (Exception e) {
            log.warn("Failed to parse user: {}", e.getMessage());
        }
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
