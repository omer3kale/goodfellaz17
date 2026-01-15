package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * User pricing tier determining access level and rates.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum UserTier {
    
    /** Standard consumer with base pricing */
    CONSUMER("consumer"),
    
    /** Reseller with volume discounts (typically 20-30% off) */
    RESELLER("reseller"),
    
    /** Agency with highest discounts and API access (typically 40-50% off) */
    AGENCY("agency");
    
    private final String value;
    
    UserTier(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static UserTier fromValue(String value) {
        for (UserTier tier : values()) {
            if (tier.value.equals(value) || tier.name().equalsIgnoreCase(value)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Unknown UserTier: " + value);
    }
}
