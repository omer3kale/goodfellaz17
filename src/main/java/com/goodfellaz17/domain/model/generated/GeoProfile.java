package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Geographic targeting profiles for order delivery.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum GeoProfile {
    
    /** Global distribution (cheapest, lowest quality) */
    WORLDWIDE("worldwide"),
    
    /** United States only */
    USA("usa"),
    
    /** United Kingdom only */
    UK("uk"),
    
    /** Germany only */
    DE("de"),
    
    /** France only */
    FR("fr"),
    
    /** Brazil only */
    BR("br"),
    
    /** Mexico only */
    MX("mx"),
    
    /** Latin America mix */
    LATAM("latam"),
    
    /** European mix (EU + UK) */
    EUROPE("europe"),
    
    /** Asian mix */
    ASIA("asia"),
    
    /** Premium mix (USA/UK/DE/FR weighted) */
    PREMIUM_MIX("premium_mix");
    
    private final String value;
    
    GeoProfile(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get the cost multiplier for this geo profile.
     * Premium geos cost more due to higher quality proxies/accounts.
     */
    public double getCostMultiplier() {
        return switch (this) {
            case WORLDWIDE -> 1.0;
            case LATAM, ASIA -> 1.2;
            case BR, MX -> 1.3;
            case EUROPE -> 1.5;
            case DE, FR -> 1.6;
            case UK -> 1.7;
            case USA -> 1.8;
            case PREMIUM_MIX -> 2.0;
        };
    }
    
    public static GeoProfile fromValue(String value) {
        for (GeoProfile profile : values()) {
            if (profile.value.equals(value) || profile.name().equalsIgnoreCase(value)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown GeoProfile: " + value);
    }
}
