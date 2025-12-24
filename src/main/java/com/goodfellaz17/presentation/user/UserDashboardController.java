package com.goodfellaz17.presentation.user;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import com.goodfellaz17.domain.model.GeoTarget;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for User Dashboard.
 * 
 * Endpoints for botzzz773.pro users:
 * - View earnings
 * - Check pending tasks
 * - User pool statistics
 */
@RestController
@RequestMapping("/user")
public class UserDashboardController {

    private final BotzzzUserProxyPool userPool;

    public UserDashboardController(BotzzzUserProxyPool userPool) {
        this.userPool = userPool;
    }

    /**
     * Get user pool statistics.
     * 
     * GET /user/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<UserPoolStats> getStats() {
        return ResponseEntity.ok(UserPoolStats.from(
                userPool.getActiveUserCount(),
                userPool.getAvailableUserCount(),
                userPool.getBusyUserCount(),
                userPool.getUsersByGeo()
        ));
    }

    /**
     * Health check for user pool.
     * 
     * GET /user/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        int available = userPool.getAvailableUserCount();
        String status = available > 0 ? "UP" : "DOWN";
        
        return ResponseEntity.ok(Map.of(
                "status", status,
                "availableUsers", available,
                "activeUsers", userPool.getActiveUserCount()
        ));
    }

    // === Response DTOs ===

    public record UserPoolStats(
            int activeUsers,
            int availableUsers,
            int busyUsers,
            Map<GeoTarget, Long> byGeo
    ) {
        public static UserPoolStats from(int active, int available, int busy, 
                                          Map<GeoTarget, Long> byGeo) {
            return new UserPoolStats(active, available, busy, byGeo);
        }
    }
}
