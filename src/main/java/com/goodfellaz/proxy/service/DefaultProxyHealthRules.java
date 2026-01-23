package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyHealthConfig;
import com.goodfellaz.proxy.model.ProxyStatus;

/**
 * Default implementation of the proxy health state machine.
 * 
 * State transitions:
 * - HEALTHY + good metrics → stays HEALTHY
 * - HEALTHY + bad metrics → DEGRADED
 * - DEGRADED + good metrics → HEALTHY (auto-recovery)
 * - DEGRADED + bad metrics → stays DEGRADED
 * - OFFLINE → always OFFLINE (terminal state, requires manual intervention)
 */
public class DefaultProxyHealthRules implements ProxyHealthRules {

    @Override
    public ProxyStatus next(ProxyStatus current, float successRate, int availableConnections, ProxyHealthConfig config) {
        // OFFLINE is terminal - never auto-recovers
        if (current == ProxyStatus.OFFLINE) {
            return ProxyStatus.OFFLINE;
        }

        // Check if metrics are good enough for HEALTHY status
        boolean metricsGood = successRate >= config.getSuccessRateThreshold() 
                && availableConnections >= config.getMinAvailableConnections();

        if (metricsGood) {
            // Good metrics: stay or become HEALTHY
            return ProxyStatus.HEALTHY;
        } else {
            // Bad metrics: degrade (or stay degraded)
            return ProxyStatus.DEGRADED;
        }
    }
}
