package com.goodfellaz17.application.arbitrage;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import com.goodfellaz17.infrastructure.user.UserStatus;

import jakarta.annotation.PostConstruct;

@Service
public class BotzzzUserProxyPool {

    private static final Logger log = LoggerFactory.getLogger(BotzzzUserProxyPool.class);

    private final Queue<UserProxy> availableUsers = new ConcurrentLinkedQueue<>();
    private final Map<String, UserProxy> allUsers = new ConcurrentHashMap<>();
    private final Map<UUID, TaskAssignment> pendingTasks = new ConcurrentHashMap<>();

    // no longer final; optional, may be null in tests
    private SimpMessagingTemplate websocket;
    private final Environment environment;

    @Value("${arbitrage.commission-rate:0.30}")
    private double commissionRate;

    @Value("${arbitrage.task-timeout-seconds:300}")
    private int taskTimeoutSeconds;

    public BotzzzUserProxyPool(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Autowired(required = false)
    public void setWebsocket(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    @PostConstruct
    public void bootstrapUserFarm() {
        log.info("Arbitrage config: commissionRate={}, taskTimeoutSeconds={}",
                commissionRate, taskTimeoutSeconds);

        if (isDevProfile()) {
            bootstrapMockUsers();
            return;
        }

        log.info("Production mode: User pool initialized empty, waiting for WebSocket connections");
    }

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
        String[] profiles = environment.getActiveProfiles();
        return profiles != null && Arrays.asList(profiles).contains("dev");
    }

    private GeoTarget parseGeoTarget(String country) {
        if (country == null || country.isBlank()) {
            return GeoTarget.WORLDWIDE;
        }

        return switch (country.toUpperCase()) {
            case "US", "USA", "UNITED STATES" -> GeoTarget.USA;
            case "GB", "DE", "FR", "IT", "ES", "NL", "BE", "AT", "CH" -> GeoTarget.EU;
            default -> GeoTarget.WORLDWIDE;
        };
    }

    public Optional<UserProxy> nextHealthyUser(GeoTarget geo) {
        Objects.requireNonNull(geo, "geo must not be null");

        int initialSize = availableUsers.size();
        for (int i = 0; i < initialSize; i++) {
            UserProxy user = availableUsers.poll();
            if (user == null) {
                break;
            }

            if (user.isHealthy() && user.matchesGeo(geo)) {
                return Optional.of(user);
            }

            if (user.isHealthy()) {
                availableUsers.add(user);
            }
        }

        return Optional.empty();
    }

    public void sendTask(UserProxy user, TaskAssignment task) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(task, "task must not be null");

        user.assignTask();
        pendingTasks.put(task.taskId(), task);

        String userId = user.getUserId();
        if (websocket != null && userId != null && !userId.isBlank()) {
            websocket.convertAndSendToUser(
                    userId,
                    "/queue/tasks",
                    task
            );
        } else {
            log.warn("Skipping WebSocket send: websocket={} userId={} proxy={}",
                    websocket, userId, user.getId());
        }

        log.info("Task sent: userId={}, taskId={}, commission={}",
                user.getUserId(), task.taskId(), task.commission());
    }

    public void completeTask(UUID taskId, String userId, int plays) {
        Objects.requireNonNull(taskId, "taskId must not be null");

        TaskAssignment task = pendingTasks.remove(taskId);
        if (task == null) {
            log.warn("completeTask called with unknown taskId={}", taskId);
            return;
        }

        if (userId == null || userId.isBlank()) {
            log.warn("completeTask called with null/blank userId for taskId={}", taskId);
            return;
        }

        UserProxy user = allUsers.get(userId);
        if (user != null) {
            user.completeTask(task.commission().doubleValue());
            availableUsers.add(user);

            log.info("Task completed: userId={}, plays={}, commission={}",
                    userId, plays, task.commission());
        } else {
            log.warn("Task {} completed but userId={} not found in pool", taskId, userId);
        }
    }

    public void registerUser(String sessionId, String userId, String ipAddress,
                             String userAgent, String country) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

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

        log.info("User registered: sessionId={}, userId={}, geo={}", sessionId, userId, geo);
    }

    public void disconnectUser(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("disconnectUser called with null/blank userId");
            return;
        }

        allUsers.values().stream()
                .filter(Objects::nonNull)
                .filter(u -> userId.equals(u.getUserId()))
                .forEach(u -> {
                    u.disconnect();
                    availableUsers.remove(u);
                });

        log.info("User disconnected: userId={}", userId);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupStaleUsers() {
        int removed = 0;
        Iterator<UserProxy> it = availableUsers.iterator();

        while (it.hasNext()) {
            UserProxy user = it.next();
            if (user == null) {
                it.remove();
                continue;
            }

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

    public int getActiveUserCount() {
        return (int) allUsers.values().stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getStatus() != UserStatus.OFFLINE)
                .count();
    }

    public int getAvailableUserCount() {
        return availableUsers.size();
    }

    public int getBusyUserCount() {
        return (int) allUsers.values().stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getStatus() == UserStatus.BUSY)
                .count();
    }

    public Map<GeoTarget, Long> getUsersByGeo() {
        Map<GeoTarget, Long> byGeo = new EnumMap<>(GeoTarget.class);
        for (GeoTarget geo : GeoTarget.values()) {
            long count = allUsers.values().stream()
                    .filter(Objects::nonNull)
                    .filter(u -> u.getGeoTarget() == geo)
                    .filter(u -> u.getStatus() != UserStatus.OFFLINE)
                    .count();
            byGeo.put(geo, count);
        }
        return byGeo;
    }
}
