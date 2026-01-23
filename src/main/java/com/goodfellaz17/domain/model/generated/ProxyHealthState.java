package com.goodfellaz17.domain.model.generated;

/**
 * Health state for proxy selection algorithm.
 * 
 * Computed from proxy_metrics.success_rate by database trigger.
 * Used by ProxyRouterService for selection decisions.
 * 
 * Selection Rules (from architecture spec):
 * - HEALTHY: successRate >= 0.85, preferred for task assignment
 * - DEGRADED: successRate >= 0.70 && < 0.85, fallback only (logged)
 * - OFFLINE: successRate < 0.70 or operational issues, never selected
 * 
 * Tier Preference (when health is equal):
 * MOBILE > RESIDENTIAL > ISP > DATACENTER
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
public enum ProxyHealthState {
    
    /**
     * successRate >= 0.85
     * Preferred for task assignment
     */
    HEALTHY(0.85, 1.0, true),
    
    /**
     * successRate >= 0.70 && < 0.85
     * Fallback only - usage should be logged
     */
    DEGRADED(0.70, 0.85, true),
    
    /**
     * successRate < 0.70 or operational issues
     * Never selected for tasks
     */
    OFFLINE(0.0, 0.70, false);
    
    private final double minSuccessRate;
    private final double maxSuccessRate;
    private final boolean selectable;
    
    ProxyHealthState(double minSuccessRate, double maxSuccessRate, boolean selectable) {
        this.minSuccessRate = minSuccessRate;
        this.maxSuccessRate = maxSuccessRate;
        this.selectable = selectable;
    }
    
    public double getMinSuccessRate() {
        return minSuccessRate;
    }
    
    public double getMaxSuccessRate() {
        return maxSuccessRate;
    }
    
    /**
     * @return true if proxies in this state can be selected for tasks
     */
    public boolean isSelectable() {
        return selectable;
    }
    
    /**
     * @return true if this state is preferred (HEALTHY only)
     */
    public boolean isPreferred() {
        return this == HEALTHY;
    }
    
    /**
     * @return true if using this state requires logging (DEGRADED)
     */
    public boolean requiresDegradedLogging() {
        return this == DEGRADED;
    }
    
    /**
     * Compute health state from success rate.
     * 
     * @param successRate the proxy's current success rate (0.0 - 1.0)
     * @return the appropriate health state
     */
    public static ProxyHealthState fromSuccessRate(double successRate) {
        if (successRate >= HEALTHY.minSuccessRate) {
            return HEALTHY;
        } else if (successRate >= DEGRADED.minSuccessRate) {
            return DEGRADED;
        } else {
            return OFFLINE;
        }
    }
    
    /**
     * Check if a success rate meets the minimum threshold for selection.
     * 
     * @param successRate the proxy's current success rate
     * @return true if the rate is >= 0.70 (DEGRADED minimum)
     */
    public static boolean meetsMinimumThreshold(double successRate) {
        return successRate >= DEGRADED.minSuccessRate;
    }
    
    /**
     * Check if a success rate is preferred (HEALTHY threshold).
     * 
     * @param successRate the proxy's current success rate
     * @return true if the rate is >= 0.85 (HEALTHY minimum)
     */
    public static boolean isPreferredRate(double successRate) {
        return successRate >= HEALTHY.minSuccessRate;
    }
}
