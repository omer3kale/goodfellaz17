package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Proxy quality tiers ordered by quality/cost (lowest to highest).
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum ProxyTier {
    
    /** Datacenter IPs - cheapest, highest ban rate */
    DATACENTER("datacenter", 1, 0.001),
    
    /** ISP IPs - moderate quality, moderate cost */
    ISP("isp", 2, 0.01),
    
    /** Tor exit nodes - anonymity focused */
    TOR("tor", 3, 0.005),
    
    /** Residential IPs - high quality, high cost */
    RESIDENTIAL("residential", 4, 0.05),
    
    /** Mobile IPs - highest quality, highest cost */
    MOBILE("mobile", 5, 0.10);
    
    private final String value;
    private final int qualityLevel;
    private final double costPerRequest; // in USD
    
    ProxyTier(String value, int qualityLevel, double costPerRequest) {
        this.value = value;
        this.qualityLevel = qualityLevel;
        this.costPerRequest = costPerRequest;
    }
    
    public String getValue() {
        return value;
    }
    
    public int getQualityLevel() {
        return qualityLevel;
    }
    
    public double getCostPerRequest() {
        return costPerRequest;
    }
    
    /**
     * Check if this tier meets or exceeds the minimum requirement.
     */
    public boolean meetsMinimum(ProxyTier minimum) {
        return this.qualityLevel >= minimum.qualityLevel;
    }
    
    /**
     * Get the expected ban rate for this tier (rough estimate).
     */
    public double getExpectedBanRate() {
        return switch (this) {
            case DATACENTER -> 0.15;  // 15% ban rate
            case ISP -> 0.05;         // 5% ban rate
            case TOR -> 0.08;         // 8% ban rate
            case RESIDENTIAL -> 0.02; // 2% ban rate
            case MOBILE -> 0.01;      // 1% ban rate
        };
    }
    
    public static ProxyTier fromValue(String value) {
        for (ProxyTier tier : values()) {
            if (tier.value.equals(value) || tier.name().equalsIgnoreCase(value)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Unknown ProxyTier: " + value);
    }
}
