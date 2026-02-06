package com.goodfellaz17.account.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.goodfellaz17.account.service.SpotifyAccount;
import com.goodfellaz17.account.service.SpotifyAccountCreator;
import com.goodfellaz17.account.service.SpotifyAccountRepository;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/accounts")
@Service
@Profile("accounts") // Disabled for streaming demo - enable with SPRING_PROFILES_ACTIVE=accounts
public class GoodFellaz17IntegrationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GoodFellaz17IntegrationOrchestrator.class);

    @Autowired
    private SpotifyAccountCreator spotifyAccountCreator;

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    // Configuration constants for account pool management
    private static final int CRITICAL_POOL_SIZE = 20;
    private static final int HEALTHY_POOL_SIZE = 50;
    private static final String WEBHOOK_URL = "https://botzzz773.pro/webhook/accounts-status";

    /**
     * GET /api/accounts/status
     * Returns overall account pool status
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getAccountStatus() {
        return spotifyAccountRepository.findAll()
                .collectList()
                .map(accounts -> {
                    Map<String, Object> status = new HashMap<>();
                    int total = accounts.size();
                    long created = accounts.stream().filter(a -> "CREATED".equals(a.getStatus())).count();
                    long active = accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
                    long failed = accounts.stream()
                            .filter(a -> "DEGRADED".equals(a.getStatus()) || "BANNED".equals(a.getStatus()))
                            .count();

                    status.put("totalAccounts", total);
                    status.put("createdAccounts", created);
                    status.put("activeAccounts", active);
                    status.put("failedAccounts", failed);
                    status.put("createdToday", countCreatedToday(accounts));
                    status.put("timestamp", LocalDateTime.now());

                    log.info("Account status retrieved: {} total, {} active", total, active);
                    return ResponseEntity.ok(status);
                });
    }

    /**
     * POST /api/accounts/create-now
     * Force immediate account creation (bypass scheduler)
     */
    @PostMapping("/create-now")
    public ResponseEntity<Map<String, String>> createAccountNow() {
        Map<String, String> response = new HashMap<>();
        try {
            CompletableFuture<SpotifyAccount> spotify =
                    spotifyAccountCreator.processNextPendingGmxAccount();

            // Wait for completion (propagates runtime failures)
            spotify.join();

            response.put("status", "success");
            response.put("message", "Account creation triggered");
            log.info("Manual Spotify account creation triggered");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to create account on demand", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * GET /api/accounts/health
     * Returns detailed health metrics
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getAccountHealth() {
        return spotifyAccountRepository.findAll()
                .collectList()
                .map(accounts -> {
                    Map<String, Object> health = new HashMap<>();

                    long healthy = accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
                    long degraded = accounts.stream().filter(a -> "DEGRADED".equals(a.getStatus())).count();
                    long banned = accounts.stream().filter(a -> "BANNED".equals(a.getStatus())).count();
                    int total = accounts.size();

                    double healthScore = total > 0 ? (healthy * 100.0) / total : 0.0;

                    health.put("healthyAccounts", healthy);
                    health.put("degradedAccounts", degraded);
                    health.put("bannedAccounts", banned);
                    health.put("totalAccounts", total);
                    health.put("healthScore", String.format("%.2f%%", healthScore));
                    health.put("poolStatus",
                            healthy >= HEALTHY_POOL_SIZE ? "OPTIMAL"
                                    : healthy >= CRITICAL_POOL_SIZE ? "WARNING" : "CRITICAL");
                    health.put("creationRate", calculateCreationRate(accounts));
                    health.put("timestamp", LocalDateTime.now());

                    log.info("Health check: {} healthy, {} degraded, {} banned", healthy, degraded, banned);
                    return ResponseEntity.ok(health);
                });
    }

    /**
     * GET /api/accounts/next-available
     * Get next available account for order fulfillment
     */
    @GetMapping("/next-available")
    public Mono<ResponseEntity<Map<String, Object>>> getNextAvailableAccount() {
        return spotifyAccountRepository.findByStatus("ACTIVE")
                .next()
                .map(account -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("email", account.getEmail());
                    response.put("spotifyUserId", account.getSpotifyUserId());
                    response.put("riskScore", calculateRiskScore(account));
                    response.put("available", true);
                    response.put("timestamp", LocalDateTime.now());

                    log.info("Next available account retrieved: {}", account.getEmail());
                    return ResponseEntity.ok(response);
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.of(
                        "available", false,
                        "message", "No active accounts available",
                        "timestamp", LocalDateTime.now())));
    }

    /**
     * POST /api/accounts/webhook/order-placed
     * Receive orders from botzzz773.pro
     */
    @PostMapping("/webhook/order-placed")
    public ResponseEntity<Map<String, String>> handleOrderWebhook(@RequestBody Map<String, Object> orderData) {
        Map<String, String> response = new HashMap<>();

        try {
            String orderId = (String) orderData.get("orderId");
            Integer quantity = (Integer) orderData.get("quantity");

            log.info("Order received: {} for {} plays", orderId, quantity);

            response.put("status", "received");
            response.put("orderId", orderId);
            response.put("message", "Order acknowledged");

            return ResponseEntity.ok(response);
        } catch (ClassCastException | NullPointerException e) {
            // Malformed payload (wrong types or missing fields) -> treat as bad request
            log.warn("Invalid order webhook payload: {}", orderData, e);
            response.put("status", "error");
            response.put("message", "Invalid order payload");
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            // Unexpected runtime failure
            log.error("Failed to process order webhook", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * GET /api/accounts/metrics
     * Prometheus format metrics
     */
    @GetMapping("/metrics")
    public Mono<ResponseEntity<String>> getMetrics() {
        return spotifyAccountRepository.findAll()
                .collectList()
                .map(accounts -> {
                    StringBuilder metrics = new StringBuilder();

                    long active = accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
                    long total = accounts.size();

                    metrics.append("# HELP spotify_accounts_total Total number of Spotify accounts\n");
                    metrics.append("# TYPE spotify_accounts_total gauge\n");
                    metrics.append("spotify_accounts_total ").append(total).append("\n");

                    metrics.append("# HELP spotify_accounts_active Active Spotify accounts\n");
                    metrics.append("# TYPE spotify_accounts_active gauge\n");
                    metrics.append("spotify_accounts_active ").append(active).append("\n");

                    metrics.append("# HELP spotify_creator_metrics Spotify creator metrics\n");
                    metrics.append("# TYPE spotify_creator_metrics gauge\n");
                    spotifyAccountCreator.getMetrics().forEach(
                            (k, v) -> metrics.append("spotify_creator_").append(k).append(" ").append(v).append("\n"));

                    return ResponseEntity.ok(metrics.toString());
                });
    }

    /**
     * Background task: Monitor account health
     * Runs every 1 hour
     */
    @Scheduled(fixedRate = 3600000)
    public void monitorAccountHealth() {
        log.info("Starting account health monitoring task");
        try {
            spotifyAccountRepository.findAll()
                    .doOnNext(account -> {
                        if (shouldMarkDegraded(account)) {
                            account.setStatus("DEGRADED");
                            spotifyAccountRepository.save(account).subscribe();
                        }
                    })
                    .subscribe();
        } catch (RuntimeException e) {
            // Safety net: log but do not crash scheduler
            log.error("Account health monitoring failed", e);
        }
    }

    /**
     * Background task: Send metrics to webhook
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void sendMetricsToWebhook() {
        log.info("Sending metrics to webhook: {}", WEBHOOK_URL);
        try {
            spotifyAccountRepository.findAll()
                    .collectList()
                    .subscribe(accounts -> {
                        if (restTemplate == null) {
                            log.warn("RestTemplate not initialized, skipping webhook");
                            return;
                        }

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("timestamp", LocalDateTime.now());
                        long healthy = accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
                        payload.put("healthyAccounts", healthy);
                        payload.put("availableAccounts", healthy);
                        payload.put("totalAccounts", accounts.size());

                        try {
                            restTemplate.postForObject(WEBHOOK_URL, payload, String.class);
                            log.info("Metrics sent successfully to webhook");
                        } catch (RestClientException e) {
                            // HTTP or client-side error on webhook call
                            log.warn("Failed to send metrics to webhook", e);
                        }
                    });
        } catch (RuntimeException e) {
            // Safety net around reactive pipeline setup
            log.error("Metrics webhook task failed", e);
        }
    }

    /**
     * Background task: Auto-trigger account creation if pool is low
     * Runs every 5 minutes
     *
     * NOTE: GMX-based automatic creation has been removed.
     * This now only logs pool status; creation pipeline is handled elsewhere.
     */
    @Scheduled(fixedRate = 300000)
    public void autoTriggerAccountCreation() {
        log.info("Checking account pool levels for auto-trigger");
        try {
            spotifyAccountRepository.findByStatus("ACTIVE")
                    .collectList()
                    .subscribe(activeAccounts -> {
                        int activeCount = activeAccounts.size();

                        if (activeCount < HEALTHY_POOL_SIZE) {
                            log.warn("Account pool below healthy threshold: {} < {}", activeCount, HEALTHY_POOL_SIZE);
                        }

                        if (activeCount < CRITICAL_POOL_SIZE) {
                            log.error("CRITICAL: Account pool critically low: {} < {}", activeCount,
                                    CRITICAL_POOL_SIZE);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Auto-trigger account creation check failed", e);
        }
    }

    // Helper methods

    private int countCreatedToday(java.util.List<SpotifyAccount> accounts) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        return (int) accounts.stream()
                .filter(a -> a.getCreatedAt().isAfter(since))
                .count();
    }

    private String calculateCreationRate(java.util.List<SpotifyAccount> accounts) {
        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
        long createdLastHour = accounts.stream()
                .filter(a -> a.getCreatedAt().isAfter(lastHour))
                .count();
        return createdLastHour + " accounts/hour";
    }

    private int calculateRiskScore(SpotifyAccount account) {
        int score = 0;

        LocalDateTime createdTime = account.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();

        if (createdTime.isAfter(now.minusHours(24))) {
            score += 40;
        } else if (createdTime.isAfter(now.minusDays(7))) {
            score += 25;
        } else if (createdTime.isAfter(now.minusDays(30))) {
            score += 10;
        }

        String status = account.getStatus();
        if (status != null) {
            switch (status) {
                case "PENDING_EMAIL_VERIFICATION" -> score += 20;
                case "ACTIVE" -> score += 0;
                case "DEGRADED" -> score += 50;
                default -> score += 0;
            }
        }

        return Math.min(score, 100);
    }

    private boolean shouldMarkDegraded(SpotifyAccount account) {
        if (account.getLastPlayedAt() != null) {
            return account.getLastPlayedAt().isBefore(LocalDateTime.now().minusHours(24));
        }
        return false;
    }
}
