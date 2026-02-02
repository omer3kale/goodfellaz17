package com.goodfellaz17.account.controller;

import com.goodfellaz17.account.service.GmailAccountCreator;
import com.goodfellaz17.account.service.SpotifyAccountCreator;
import com.goodfellaz17.account.service.SpotifyAccount;
import com.goodfellaz17.account.service.SpotifyAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/accounts")
@Service
@Profile("accounts")  // Disabled for streaming demo - enable with SPRING_PROFILES_ACTIVE=accounts
public class GoodFellaz17IntegrationOrchestrator {

    @Autowired
    private GmailAccountCreator gmailAccountCreator;

    @Autowired
    private SpotifyAccountCreator spotifyAccountCreator;

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    @Autowired
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
                    long failed = accounts.stream().filter(a -> "DEGRADED".equals(a.getStatus()) || "BANNED".equals(a.getStatus())).count();

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
            CompletableFuture<SpotifyAccount> gmail = gmailAccountCreator.createGmxAccount();
            CompletableFuture<SpotifyAccount> spotify = spotifyAccountCreator.processNextPendingGmxAccount();

            // Wait for both to complete
            CompletableFuture.allOf(gmail, spotify).join();

            response.put("status", "success");
            response.put("message", "Account creation triggered");
            log.info("Manual account creation triggered");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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
                    health.put("poolStatus", healthy >= HEALTHY_POOL_SIZE ? "OPTIMAL" :
                                            healthy >= CRITICAL_POOL_SIZE ? "WARNING" : "CRITICAL");
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
                    "timestamp", LocalDateTime.now()
                )));
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
        } catch (Exception e) {
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

                    metrics.append("# HELP gmail_creator_metrics Gmail creator metrics\n");
                    metrics.append("# TYPE gmail_creator_metrics gauge\n");
                    gmailAccountCreator.getMetrics().forEach((k, v) ->
                        metrics.append("gmail_creator_").append(k).append(" ").append(v).append("\n")
                    );

                    metrics.append("# HELP spotify_creator_metrics Spotify creator metrics\n");
                    metrics.append("# TYPE spotify_creator_metrics gauge\n");
                    spotifyAccountCreator.getMetrics().forEach((k, v) ->
                        metrics.append("spotify_creator_").append(k).append(" ").append(v).append("\n")
                    );

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
                        // Simulate health check logic
                        if (shouldMarkDegraded(account)) {
                            account.setStatus("DEGRADED");
                            spotifyAccountRepository.save(account).subscribe();
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
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
                        payload.put("healthyAccounts", accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count());
                        payload.put("availableAccounts", accounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count());
                        payload.put("totalAccounts", accounts.size());

                        try {
                            restTemplate.postForObject(WEBHOOK_URL, payload, String.class);
                            log.info("Metrics sent successfully to webhook");
                        } catch (Exception e) {
                            log.warn("Failed to send metrics to webhook", e);
                        }
                    });
        } catch (Exception e) {
            log.error("Metrics webhook task failed", e);
        }
    }

    /**
     * Background task: Auto-trigger account creation if pool is low
     * Runs every 5 minutes
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
                            try {
                                gmailAccountCreator.createGmxAccount().thenAccept(account ->
                                    log.info("Auto-triggered Gmail account creation")
                                ).exceptionally(e -> {
                                    log.error("Auto-trigger failed", e);
                                    return null;
                                });
                            } catch (Exception e) {
                                log.error("Failed to trigger account creation", e);
                            }
                        }

                        if (activeCount < CRITICAL_POOL_SIZE) {
                            log.error("CRITICAL: Account pool critically low: {} < {}", activeCount, CRITICAL_POOL_SIZE);
                            // FIXED: Trigger emergency account creation
                            createEmergencyAccounts(5).subscribe();
                        }
                    });
        } catch (Exception e) {
            log.error("Auto-trigger account creation failed", e);
        }
    }

    // Helper methods

    private int countCreatedToday(java.util.List<SpotifyAccount> accounts) {
        LocalDateTime today = LocalDateTime.now().minusDays(1);
        return (int) accounts.stream()
                .filter(a -> a.getCreatedAt().isAfter(today))
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

        // Newer accounts = higher risk
        LocalDateTime createdTime = account.getCreatedAt();
        if (createdTime.isAfter(LocalDateTime.now().minusHours(24))) {
            score += 40;
        } else if (createdTime.isAfter(LocalDateTime.now().minusDays(7))) {
            score += 25;
        } else if (createdTime.isAfter(LocalDateTime.now().minusDays(30))) {
            score += 10;
        }

        // Account status
        if ("PENDING_EMAIL_VERIFICATION".equals(account.getStatus())) {
            score += 20;
        } else if ("ACTIVE".equals(account.getStatus())) {
            score += 0;
        } else if ("DEGRADED".equals(account.getStatus())) {
            score += 50;
        }

        return Math.min(score, 100);
    }

    private boolean shouldMarkDegraded(SpotifyAccount account) {
        // Implement actual health check logic
        // For now, mark as degraded if no plays in 24 hours
        if (account.getLastPlayedAt() != null) {
            return account.getLastPlayedAt().isBefore(LocalDateTime.now().minusHours(24));
        }
        return false;
    }

    private Mono<Void> createEmergencyAccounts(int count) {
        log.info("🚨 EMERGENCY: Creating {} accounts to restore pool health", count);
        return Flux.range(0, count)
                .flatMap(i -> {
                    CompletableFuture<SpotifyAccount> future = gmailAccountCreator.createGmxAccount();
                    return Mono.fromFuture(future);
                })
                .doOnNext(account -> log.info("Emergency account created: {}", account.getEmail()))
                .then();
    }
}
