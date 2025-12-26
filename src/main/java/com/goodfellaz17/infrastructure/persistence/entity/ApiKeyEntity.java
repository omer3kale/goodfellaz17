package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API Key Entity - Customer wallet.
 * Maps to api_keys table for R2DBC.
 */
@Table("api_keys")
public class ApiKeyEntity {

    @Id
    private UUID id;

    @Column("api_key")
    private String apiKey;

    @Column("user_email")
    private String userEmail;

    @Column("user_name")
    private String userName;

    @Column("balance")
    private BigDecimal balance;

    @Column("total_spent")
    private BigDecimal totalSpent;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private Instant createdAt;

    // Default constructor for R2DBC
    public ApiKeyEntity() {}

    public ApiKeyEntity(String apiKey, String userEmail, String userName, BigDecimal balance) {
        this.id = UUID.randomUUID();
        this.apiKey = apiKey;
        this.userEmail = userEmail;
        this.userName = userName;
        this.balance = balance;
        this.totalSpent = BigDecimal.ZERO;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
