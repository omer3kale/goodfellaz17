package com.goodfellaz17.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ProductionGuardConfig - Hardening guards for production deployments.
 *
 * Enforces:
 * - Time multiplier = 1.0x in prod (no compression)
 * - Failure injection disabled
 * - Rate limiting on 15k orders
 * - Chaos endpoints not exposed
 * - Safety thresholds on proxy/account pools
 *
 * Activated only when @Profile("prod")
 *
 * @author goodfellaz17
 * @since 1.0.0
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(ProductionGuardProperties.class)
public class ProductionGuardConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductionGuardConfig.class);

    private final ProductionGuardProperties properties;

    public ProductionGuardConfig(ProductionGuardProperties properties) {
        this.properties = properties;

        // Log guard enforcement
        log.info("========================================");
        log.info("PRODUCTION GUARD CONFIG LOADED");
        log.info("========================================");
        log.info("Max concurrent orders (circuit breaker): {}", properties.getMaxConcurrentOrders());
        log.info("Rate limit enabled: {}", properties.getRateLimit().isEnabled());
        log.info("Rate limit per minute: {}", properties.getRateLimit().getOrdersPerMinute());
        log.info("Min healthy proxies: {}", properties.getMinHealthyProxies());
        log.info("Min healthy accounts: {}", properties.getMinHealthyAccounts());
        log.info("Failure injection DISABLED (forced)");
        log.info("Time multiplier: 1.0x (no compression)");
        log.info("Chaos endpoints: DISABLED");
        log.info("========================================");
    }

    public boolean isTimeCompressionAllowed() {
        // NEVER allow time multiplier != 1.0 in production
        return false;
    }

    public boolean isFailureInjectionAllowed() {
        // NEVER allow failure injection in production
        return false;
    }

    public boolean isChaosEndpointsExposed() {
        // NEVER expose chaos endpoints in production
        return false;
    }

    public int getMaxConcurrentOrders() {
        return properties.getMaxConcurrentOrders();
    }

    public RateLimitProperties getRateLimitConfig() {
        return properties.getRateLimit();
    }

    public int getMinHealthyProxies() {
        return properties.getMinHealthyProxies();
    }

    public int getMinHealthyAccounts() {
        return properties.getMinHealthyAccounts();
    }
}

/**
 * Properties for production guards.
 * Loaded from application-prod.yml
 */
@ConfigurationProperties(prefix = "app.safety")
class ProductionGuardProperties {

    private int maxConcurrentOrders = 15;
    private RateLimitProperties rateLimit = new RateLimitProperties();
    private int minHealthyProxies = 20;
    private int minHealthyAccounts = 300;

    public int getMaxConcurrentOrders() {
        return maxConcurrentOrders;
    }

    public void setMaxConcurrentOrders(int maxConcurrentOrders) {
        this.maxConcurrentOrders = maxConcurrentOrders;
    }

    public RateLimitProperties getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitProperties rateLimit) {
        this.rateLimit = rateLimit;
    }

    public int getMinHealthyProxies() {
        return minHealthyProxies;
    }

    public void setMinHealthyProxies(int minHealthyProxies) {
        this.minHealthyProxies = minHealthyProxies;
    }

    public int getMinHealthyAccounts() {
        return minHealthyAccounts;
    }

    public void setMinHealthyAccounts(int minHealthyAccounts) {
        this.minHealthyAccounts = minHealthyAccounts;
    }
}

/**
 * Rate limiting configuration for production.
 */
class RateLimitProperties {
    private boolean enabled = true;
    private int ordersPerMinute = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrdersPerMinute() {
        return ordersPerMinute;
    }

    public void setOrdersPerMinute(int ordersPerMinute) {
        this.ordersPerMinute = ordersPerMinute;
    }
}
