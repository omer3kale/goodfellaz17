package com.goodfellaz17.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate Limiting Config - 100 requests/minute per API key.
 * 
 * Simple in-memory rate limiter for API endpoints.
 * For production, consider Redis-based implementation.
 */
@Component
public class RateLimitingConfig implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingConfig.class);

    // Rate limit: 100 requests per minute per API key
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_MS = 60_000; // 1 minute

    // In-memory store: apiKey → RateLimitBucket
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only rate-limit API endpoints
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        // Skip webhook (Stripe needs to send events)
        if (path.contains("/webhook")) {
            return chain.filter(exchange);
        }

        // Extract API key from query param or header
        String apiKey = extractApiKey(exchange);

        if (apiKey == null || apiKey.isBlank()) {
            // No API key = use IP-based limiting
            apiKey = "ip:" + exchange.getRequest().getRemoteAddress();
        }

        // Check rate limit
        RateLimitBucket bucket = buckets.computeIfAbsent(apiKey, k -> new RateLimitBucket());

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for: {}", apiKey);
            
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
            exchange.getResponse().getHeaders().add("Retry-After", "60");
            
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory()
                    .wrap("{\"error\":\"Rate limit exceeded. Max 100 requests/minute.\"}".getBytes()))
            );
        }

        // Add rate limit headers
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining()));

        return chain.filter(exchange);
    }

    /**
     * Extract API key from request.
     */
    private String extractApiKey(ServerWebExchange exchange) {
        // Try query param first
        String key = exchange.getRequest().getQueryParams().getFirst("key");
        if (key != null && !key.isBlank()) {
            return key;
        }

        // Try apiKey param
        key = exchange.getRequest().getQueryParams().getFirst("apiKey");
        if (key != null && !key.isBlank()) {
            return key;
        }

        // Try X-API-Key header
        key = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (key != null && !key.isBlank()) {
            return key;
        }

        return null;
    }

    /**
     * Simple sliding window rate limit bucket.
     */
    private static class RateLimitBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().toEpochMilli();

        public boolean tryConsume() {
            long now = Instant.now().toEpochMilli();

            // Reset window if expired
            if (now - windowStart > WINDOW_MS) {
                synchronized (this) {
                    if (now - windowStart > WINDOW_MS) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }

            // Try to consume
            int current = count.incrementAndGet();
            return current <= MAX_REQUESTS_PER_MINUTE;
        }

        public int getRemaining() {
            return Math.max(0, MAX_REQUESTS_PER_MINUTE - count.get());
        }
    }

    /**
     * Clean up old buckets periodically.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000) // 5 minutes
    public void cleanupBuckets() {
        long now = Instant.now().toEpochMilli();
        buckets.entrySet().removeIf(entry -> 
            now - entry.getValue().windowStart > WINDOW_MS * 5
        );
        log.debug("Rate limit buckets cleanup: {} active", buckets.size());
    }
}
