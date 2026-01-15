package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Balance transaction types.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum TransactionType {
    
    /** Deduction for order payment */
    DEBIT("debit"),
    
    /** Addition from deposit */
    CREDIT("credit"),
    
    /** Refund for failed/cancelled order */
    REFUND("refund"),
    
    /** Promotional bonus */
    BONUS("bonus"),
    
    /** Manual admin adjustment */
    ADJUSTMENT("adjustment");
    
    private final String value;
    
    TransactionType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public boolean isPositive() {
        return this == CREDIT || this == REFUND || this == BONUS;
    }
    
    public boolean isNegative() {
        return this == DEBIT;
    }
    
    public static TransactionType fromValue(String value) {
        for (TransactionType type : values()) {
            if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransactionType: " + value);
    }
}
