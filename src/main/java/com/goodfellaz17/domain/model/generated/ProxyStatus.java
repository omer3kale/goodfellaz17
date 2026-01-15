package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Proxy node operational status.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum ProxyStatus {
    
    /** Online and accepting connections */
    ONLINE("online"),
    
    /** Offline, not responding to healthchecks */
    OFFLINE("offline"),
    
    /** Under maintenance, temporarily unavailable */
    MAINTENANCE("maintenance"),
    
    /** Banned by target platform, should not be used */
    BANNED("banned"),
    
    /** Rate limited, temporary cooldown */
    RATE_LIMITED("rate_limited");
    
    private final String value;
    
    ProxyStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public boolean isUsable() {
        return this == ONLINE;
    }
    
    public boolean needsCooldown() {
        return this == RATE_LIMITED;
    }
    
    public static ProxyStatus fromValue(String value) {
        for (ProxyStatus status : values()) {
            if (status.value.equals(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ProxyStatus: " + value);
    }
}
