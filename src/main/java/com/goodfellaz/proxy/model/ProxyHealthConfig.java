package com.goodfellaz.proxy.model;

/**
 * Configuration thresholds for proxy health evaluation.
 * Used by ProxyHealthRules to determine state transitions.
 */
public class ProxyHealthConfig {
    private final float successRateThreshold;
    private final int minAvailableConnections;

    public ProxyHealthConfig(float successRateThreshold, int minAvailableConnections) {
        this.successRateThreshold = successRateThreshold;
        this.minAvailableConnections = minAvailableConnections;
    }

    public float getSuccessRateThreshold() {
        return successRateThreshold;
    }

    public int getMinAvailableConnections() {
        return minAvailableConnections;
    }
}
