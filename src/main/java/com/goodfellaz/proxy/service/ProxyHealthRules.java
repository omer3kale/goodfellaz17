package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyHealthConfig;
import com.goodfellaz.proxy.model.ProxyStatus;

/**
 * Health state machine interface for proxy nodes.
 * Determines the next health state based on current state and metrics.
 */
public interface ProxyHealthRules {
    
    /**
     * Evaluate the next health state for a proxy based on current metrics.
     *
     * @param current Current health status of the proxy
     * @param successRate Current success rate (0.0 - 100.0)
     * @param availableConnections Number of available connections in the pool
     * @param config Threshold configuration for health evaluation
     * @return The next health status after applying rules
     */
    ProxyStatus next(ProxyStatus current, float successRate, int availableConnections, ProxyHealthConfig config);
}
