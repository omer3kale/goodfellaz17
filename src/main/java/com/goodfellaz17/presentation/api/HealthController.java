package com.goodfellaz17.presentation.api;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.application.service.RoutingEngine;
import com.goodfellaz17.domain.port.ProxyStrategy;
import com.goodfellaz17.infrastructure.proxy.health.ProxyHealthChecker;
import com.goodfellaz17.infrastructure.spotify.SpotifyTokenService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Health Controller - Production monitoring endpoints.
 * 
 * Provides real-time status of all system components:
 * - Proxy health (all 5 sources)
 * - Spotify token status
 * - Routing capacity
 * - User arbitrage pool
 */
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    private final RoutingEngine routingEngine;
    private final Optional<ProxyHealthChecker> proxyHealthChecker;
    private final Optional<SpotifyTokenService> spotifyTokenService;
    private final BotzzzUserProxyPool userProxyPool;

    public HealthController(
            RoutingEngine routingEngine,
            Optional<ProxyHealthChecker> proxyHealthChecker,
            Optional<SpotifyTokenService> spotifyTokenService,
            BotzzzUserProxyPool userProxyPool) {
        this.routingEngine = routingEngine;
        this.proxyHealthChecker = proxyHealthChecker;
        this.spotifyTokenService = spotifyTokenService;
        this.userProxyPool = userProxyPool;
    }

    /**
     * Main health check - all systems status.
     */
    @GetMapping
    public HealthResponse health() {
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();
        
        return new HealthResponse(
            "UP",
            stats.enabledSources(),
            stats.totalCapacity(),
            stats.totalRemaining(),
            userProxyPool.getActiveUserCount(),
            spotifyTokenService.map(s -> s.getStats().get("valid_tokens")).orElse(0),
            Instant.now()
        );
    }

    /**
     * Detailed proxy health across all sources.
     */
    @GetMapping("/proxies")
    public ProxyHealthResponse proxyHealth() {
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();
        
        int healthyCount = proxyHealthChecker
            .map(c -> (int) c.getHealthStatus().values().stream().filter(h -> h).count())
            .orElse(0);
        
        return new ProxyHealthResponse(
            stats.totalSources(),
            stats.enabledSources(),
            healthyCount,
            stats.totalCapacity(),
            stats.totalRemaining(),
            stats.totalActiveLeases(),
            userProxyPool.getAvailableUserCount(),
            userProxyPool.getBusyUserCount()
        );
    }

    /**
     * Spotify token status for farm accounts.
     */
    @GetMapping("/spotify")
    public SpotifyHealthResponse spotifyHealth() {
        Map<String, Object> stats = spotifyTokenService
            .map(SpotifyTokenService::getStats)
            .orElse(Map.of("total_cached", 0, "valid_tokens", 0, "expired_tokens", 0));
        
        return new SpotifyHealthResponse(
            "UP",
            (Integer) stats.getOrDefault("total_cached", 0),
            (Integer) stats.getOrDefault("valid_tokens", 0),
            (Integer) stats.getOrDefault("expired_tokens", 0)
        );
    }

    /**
     * User arbitrage pool status.
     */
    @GetMapping("/users")
    public UserPoolResponse userPool() {
        return new UserPoolResponse(
            userProxyPool.getActiveUserCount(),
            userProxyPool.getAvailableUserCount(),
            userProxyPool.getBusyUserCount(),
            userProxyPool.getUsersByGeo()
        );
    }

    /**
     * Routing engine status.
     */
    @GetMapping("/routing")
    public RoutingHealthResponse routing() {
        ProxyStrategy.AggregateStats stats = routingEngine.getStats();
        
        return new RoutingHealthResponse(
            stats.totalSources(),
            stats.enabledSources(),
            stats.totalCapacity(),
            stats.totalRemaining(),
            stats.totalActiveLeases()
        );
    }

    // === Response DTOs ===

    public record HealthResponse(
        String status,
        int routingSources,
        int totalCapacity,
        int remainingCapacity,
        int activeUsers,
        Object validTokens,
        Instant timestamp
    ) {}

    public record ProxyHealthResponse(
        int totalSources,
        int enabledSources,
        int healthyProxies,
        int totalCapacity,
        int remainingCapacity,
        int activeLeases,
        int availableUsers,
        int busyUsers
    ) {}

    public record SpotifyHealthResponse(
        String status,
        int totalCached,
        int validTokens,
        int expiredTokens
    ) {}

    public record UserPoolResponse(
        int activeUsers,
        int availableUsers,
        int busyUsers,
        Map<?, ?> usersByGeo
    ) {}

    public record RoutingHealthResponse(
        int totalSources,
        int enabledSources,
        int totalCapacity,
        int remainingCapacity,
        int activeLeases
    ) {}
}
