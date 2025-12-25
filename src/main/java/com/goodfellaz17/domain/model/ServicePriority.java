package com.goodfellaz17.domain.model;

/**
 * Service priority levels that determine routing behavior.
 * Higher priority services get premium routing paths.
 */
public enum ServicePriority {
    
    /**
     * Basic services - use cheapest available source.
     * Example: Worldwide plays, basic followers.
     */
    BASIC(1, "Basic", false),
    
    /**
     * Premium services - prefer reliable, geo-targeted sources.
     * Example: USA plays, UK listeners.
     */
    PREMIUM(2, "Premium", true),
    
    /**
     * Elite services - highest quality sources only.
     * Example: Chart plays, editorial playlist placement.
     */
    ELITE(3, "Elite", true),
    
    /**
     * High volume services - prefer high-capacity sources.
     * Example: Large drip campaigns, bulk plays.
     */
    HIGH_VOLUME(4, "High Volume", false),
    
    /**
     * Mobile emulation - prefer mobile/phone farm sources.
     * Example: Mobile-optimized plays, app-like behavior.
     */
    MOBILE_EMULATION(5, "Mobile", true);
    
    private final int level;
    private final String displayName;
    private final boolean requiresPremiumSource;
    
    ServicePriority(int level, String displayName, boolean requiresPremiumSource) {
        this.level = level;
        this.displayName = displayName;
        this.requiresPremiumSource = requiresPremiumSource;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean requiresPremiumSource() {
        return requiresPremiumSource;
    }
}
