package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * User account status.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum UserStatus {
    
    /** Active and can place orders */
    ACTIVE("active"),
    
    /** Suspended due to fraud, abuse, or non-payment */
    SUSPENDED("suspended"),
    
    /** Email verification pending */
    PENDING_VERIFICATION("pending_verification");
    
    private final String value;
    
    UserStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static UserStatus fromValue(String value) {
        for (UserStatus status : values()) {
            if (status.value.equals(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus: " + value);
    }
}
