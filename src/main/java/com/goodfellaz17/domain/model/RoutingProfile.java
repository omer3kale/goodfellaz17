package com.goodfellaz17.domain.model;

/**
 * Routing profile attached to each service, determining how orders are routed.
 * This is configuration-driven and invisible to the frontend.
 * 
 * @param priority          Service priority level
 * @param geoSensitive      Whether geographic targeting matters for this service
 * @param needsMobileLikeBehavior  Whether mobile/phone farm should be preferred
 * @param maxRiskTolerance  0.0 = ultra-safe only, 1.0 = any source acceptable
 */
public record RoutingProfile(
    ServicePriority priority,
    boolean geoSensitive,
    boolean needsMobileLikeBehavior,
    double maxRiskTolerance
) {
    
    /**
     * Default profile for unknown services - basic, not geo-sensitive.
     */
    public static final RoutingProfile DEFAULT = new RoutingProfile(
        ServicePriority.BASIC, false, false, 0.5
    );
    
    /**
     * Profile for premium geo-targeted services.
     */
    public static RoutingProfile premium(String... targetGeos) {
        return new RoutingProfile(ServicePriority.PREMIUM, true, false, 0.3);
    }
    
    /**
     * Profile for elite/chart services - mobile preferred.
     */
    public static RoutingProfile elite() {
        return new RoutingProfile(ServicePriority.ELITE, true, true, 0.1);
    }
    
    /**
     * Profile for high-volume bulk services.
     */
    public static RoutingProfile highVolume() {
        return new RoutingProfile(ServicePriority.HIGH_VOLUME, false, false, 0.7);
    }
    
    /**
     * Profile for mobile-emulation services.
     */
    public static RoutingProfile mobileEmulation() {
        return new RoutingProfile(ServicePriority.MOBILE_EMULATION, false, true, 0.2);
    }
    
    /**
     * Check if this profile allows a source with given risk level.
     */
    public boolean allowsRisk(double sourceRisk) {
        return sourceRisk <= maxRiskTolerance;
    }
}
