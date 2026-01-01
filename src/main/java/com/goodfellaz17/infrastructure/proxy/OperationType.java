package com.goodfellaz17.infrastructure.proxy;

/**
 * Operation types for proxy routing decisions.
 * 
 * Different operations have different risk profiles and proxy requirements:
 * - High-risk ops (account creation) need premium residential/mobile
 * - Low-risk ops (searches) can use cheaper datacenter proxies
 */
public enum OperationType {
    
    /**
     * Creating new Spotify accounts - HIGHEST RISK.
     * Requires: Mobile or premium residential, geo-accurate, fresh IPs.
     * Cost tolerance: HIGH (worth paying for success)
     */
    ACCOUNT_CREATION(1.0, true, true),
    
    /**
     * Email verification flows - HIGH RISK.
     * Requires: Residential preferred, sticky session.
     * Cost tolerance: MEDIUM-HIGH
     */
    EMAIL_VERIFICATION(0.8, true, true),
    
    /**
     * Stream/play operations - MEDIUM RISK.
     * Requires: Mix of residential/ISP, rotation allowed.
     * Cost tolerance: MEDIUM (volume operation)
     */
    STREAM_OPERATION(0.5, false, true),
    
    /**
     * Initial queries, searches, metadata - LOW RISK.
     * Requires: Any proxy tier works, high rotation.
     * Cost tolerance: LOW (optimize for cost)
     */
    INITIAL_QUERY(0.2, false, false),
    
    /**
     * Playlist operations (follow, save) - MEDIUM RISK.
     * Requires: Residential preferred.
     * Cost tolerance: MEDIUM
     */
    PLAYLIST_OPERATION(0.6, false, true),
    
    /**
     * Follow artist/user - MEDIUM RISK.
     * Requires: Residential preferred.
     * Cost tolerance: MEDIUM
     */
    FOLLOW_OPERATION(0.6, false, true),
    
    /**
     * Health checks, internal ops - LOWEST RISK.
     * Requires: Any available proxy.
     * Cost tolerance: LOWEST
     */
    HEALTH_CHECK(0.1, false, false);
    
    private final double riskLevel;        // 0.0-1.0, higher = more scrutiny
    private final boolean requiresSticky;   // Needs same IP for session
    private final boolean requiresGeoMatch; // Needs geo-accurate IP
    
    OperationType(double riskLevel, boolean requiresSticky, boolean requiresGeoMatch) {
        this.riskLevel = riskLevel;
        this.requiresSticky = requiresSticky;
        this.requiresGeoMatch = requiresGeoMatch;
    }
    
    public double getRiskLevel() { return riskLevel; }
    public boolean requiresSticky() { return requiresSticky; }
    public boolean requiresGeoMatch() { return requiresGeoMatch; }
    
    /**
     * Get minimum acceptable proxy tier for this operation.
     */
    public ProxyTier getMinimumTier() {
        if (riskLevel >= 0.8) return ProxyTier.MOBILE;
        if (riskLevel >= 0.5) return ProxyTier.RESIDENTIAL;
        if (riskLevel >= 0.3) return ProxyTier.ISP;
        return ProxyTier.DATACENTER;
    }
}
