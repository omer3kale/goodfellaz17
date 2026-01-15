package com.goodfellaz17.infrastructure.health;

import com.goodfellaz17.application.service.BotOrchestratorService;
import com.goodfellaz17.infrastructure.bot.PremiumAccountFarm;
import com.goodfellaz17.infrastructure.bot.ResidentialProxyPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Infrastructure Health Indicator - Bot system health.
 * 
 * Exposes health status via /actuator/health endpoint.
 * System is healthy when:
 * - At least 10 healthy proxies available
 * - At least 10 healthy accounts available (relaxed in local profile)
 * - Execution capacity available
 */
@Component
public class BotHealthIndicator implements HealthIndicator {

    private static final int MIN_HEALTHY_PROXIES = 10;
    private static final int MIN_HEALTHY_ACCOUNTS = 10;

    private final ResidentialProxyPool proxyPool;
    private final PremiumAccountFarm accountFarm;
    private final BotOrchestratorService orchestratorService;
    
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public BotHealthIndicator(ResidentialProxyPool proxyPool,
                              PremiumAccountFarm accountFarm,
                              BotOrchestratorService orchestratorService) {
        this.proxyPool = proxyPool;
        this.accountFarm = accountFarm;
        this.orchestratorService = orchestratorService;
    }

    @Override
    public Health health() {
        int healthyProxies = proxyPool.healthyCount();
        int healthyAccounts = accountFarm.healthyCount();
        var stats = orchestratorService.getStats();
        
        // In local profile, relax account requirement for testing
        boolean isLocalProfile = "local".equals(activeProfile);
        int requiredAccounts = isLocalProfile ? 0 : MIN_HEALTHY_ACCOUNTS;

        Health.Builder builder = healthyProxies >= MIN_HEALTHY_PROXIES 
                && healthyAccounts >= requiredAccounts
                ? Health.up()
                : Health.down();

        return builder
                .withDetail("healthyProxies", healthyProxies)
                .withDetail("healthyAccounts", healthyAccounts)
                .withDetail("activeBotTasks", stats.activeBotTasks())
                .withDetail("hasCapacity", stats.hasCapacity())
                .withDetail("pendingOrders", stats.pendingOrders())
                .withDetail("profile", isLocalProfile ? "local (relaxed)" : "production")
                .build();
    }
}
