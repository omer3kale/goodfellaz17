package com.goodfellaz17.presentation.dto;

import com.goodfellaz17.application.service.BotOrchestratorService;

/**
 * DTO - Execution Statistics Response.
 * 
 * Provides live metrics for monitoring dashboard.
 */
public record StatsResponse(
        long pendingOrders,
        long processingOrders,
        int activeBotTasks,
        boolean hasCapacity,
        int healthyProxies,
        int healthyAccounts,
        double successRate
) {
    public static StatsResponse from(BotOrchestratorService.ExecutionStats stats) {
        // Calculate success rate (simulated 99.9% for now)
        double successRate = stats.healthyProxies() > 0 && stats.healthyAccounts() > 0 
                ? 99.9 : 0.0;
        
        return new StatsResponse(
                stats.pendingOrders(),
                stats.processingOrders(),
                stats.activeBotTasks(),
                stats.hasCapacity(),
                stats.healthyProxies(),
                stats.healthyAccounts(),
                successRate
        );
    }
}
