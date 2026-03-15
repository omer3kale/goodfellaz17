package com.goodfellaz17.delivery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant aggregate root — customer organization.
 * 
 * Enforces:
 * - Unique tenant code (for API key prefixes)
 * - Active/inactive status
 * - Credit balance tracking
 */
public class Tenant {
    
    private final UUID id;
    private final String code;
    private final String name;
    
    private String contactEmail;
    private boolean active;
    private BigDecimal creditBalance;
    
    private final Instant createdAt;
    private Instant updatedAt;
    
    public Tenant(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = true;
        this.creditBalance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public UUID getId() {
        return id;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getContactEmail() {
        return contactEmail;
    }
    
    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
        this.updatedAt = Instant.now();
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
    
    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }
    
    public BigDecimal getCreditBalance() {
        return creditBalance;
    }
    
    public void addCredit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cannot add negative credit");
        }
        this.creditBalance = creditBalance.add(amount);
        this.updatedAt = Instant.now();
    }
    
    public void debitCredit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cannot debit negative amount");
        }
        if (creditBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient credit: " + creditBalance + " < " + amount);
        }
        this.creditBalance = creditBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    @Override
    public String toString() {
        return "Tenant{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", creditBalance=" + creditBalance +
                ", createdAt=" + createdAt +
                '}';
    }
}
