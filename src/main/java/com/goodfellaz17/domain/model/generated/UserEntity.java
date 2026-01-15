package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Entity: User
 * Table: users
 * 
 * Represents a platform user with tiered pricing (CONSUMER/RESELLER/AGENCY).
 * Supports API key authentication and webhook notifications.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("users")
public class UserEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Email
    @Size(max = 255)
    @Column("email")
    private String email;
    
    @NotNull
    @Size(max = 255)
    @Column("password_hash")
    private String passwordHash;
    
    /**
     * User tier determining pricing: CONSUMER, RESELLER, AGENCY
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("tier")
    private String tier = UserTier.CONSUMER.name();
    
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("999999.99")
    @Column("balance")
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Nullable
    @Size(max = 64)
    @Column("api_key")
    private String apiKey;
    
    @Nullable
    @Size(max = 512)
    @Column("webhook_url")
    private String webhookUrl;
    
    @Nullable
    @Size(max = 512)
    @Column("discord_webhook")
    private String discordWebhook;
    
    @Nullable
    @Size(max = 255)
    @Column("company_name")
    private String companyName;
    
    @Nullable
    @Size(max = 32)
    @Column("referral_code")
    private String referralCode;
    
    @Nullable
    @Column("referred_by")
    private UUID referredBy;
    
    @NotNull
    @CreatedDate
    @Column("created_at")
    private Instant createdAt = Instant.now();
    
    @Nullable
    @Column("last_login")
    private Instant lastLogin;
    
    /**
     * Account status: ACTIVE, SUSPENDED, PENDING_VERIFICATION
     * Stored as STRING in database for forward compatibility.
     */
    @NotNull
    @Column("status")
    private String status = UserStatus.ACTIVE.name();
    
    @NotNull
    @Column("email_verified")
    private Boolean emailVerified = false;
    
    @NotNull
    @Column("two_factor_enabled")
    private Boolean twoFactorEnabled = false;
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    public UserEntity markNotNew() {
        this.isNew = false;
        return this;
    }

    // Default constructor for R2DBC
    public UserEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.isNew = true;
    }
    
    // Builder pattern constructor
    private UserEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.email = Objects.requireNonNull(builder.email, "email is required");
        this.passwordHash = Objects.requireNonNull(builder.passwordHash, "passwordHash is required");
        this.tier = builder.tier != null ? builder.tier : UserTier.CONSUMER.name();
        this.balance = builder.balance != null ? builder.balance : BigDecimal.ZERO;
        this.apiKey = builder.apiKey;
        this.webhookUrl = builder.webhookUrl;
        this.discordWebhook = builder.discordWebhook;
        this.companyName = builder.companyName;
        this.referralCode = builder.referralCode;
        this.referredBy = builder.referredBy;
        this.createdAt = Instant.now();
        this.lastLogin = builder.lastLogin;
        this.status = builder.status != null ? builder.status : UserStatus.ACTIVE.name();
        this.emailVerified = builder.emailVerified != null ? builder.emailVerified : false;
        this.twoFactorEnabled = builder.twoFactorEnabled != null ? builder.twoFactorEnabled : false;
        this.isNew = true;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getTier() { return tier; }
    public UserTier getTierEnum() { return UserTier.valueOf(tier); }
    public BigDecimal getBalance() { return balance; }
    @Nullable public String getApiKey() { return apiKey; }
    @Nullable public String getWebhookUrl() { return webhookUrl; }
    @Nullable public String getDiscordWebhook() { return discordWebhook; }
    @Nullable public String getCompanyName() { return companyName; }
    @Nullable public String getReferralCode() { return referralCode; }
    @Nullable public UUID getReferredBy() { return referredBy; }
    public Instant getCreatedAt() { return createdAt; }
    @Nullable public Instant getLastLogin() { return lastLogin; }
    public String getStatus() { return status; }
    public UserStatus getStatusEnum() { return UserStatus.valueOf(status); }
    public Boolean getEmailVerified() { return emailVerified; }
    public Boolean getTwoFactorEnabled() { return twoFactorEnabled; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setTier(String tier) { this.tier = tier; }
    public void setTier(UserTier tier) { this.tier = tier.name(); }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setApiKey(@Nullable String apiKey) { this.apiKey = apiKey; }
    public void setWebhookUrl(@Nullable String webhookUrl) { this.webhookUrl = webhookUrl; }
    public void setDiscordWebhook(@Nullable String discordWebhook) { this.discordWebhook = discordWebhook; }
    public void setCompanyName(@Nullable String companyName) { this.companyName = companyName; }
    public void setReferralCode(@Nullable String referralCode) { this.referralCode = referralCode; }
    public void setReferredBy(@Nullable UUID referredBy) { this.referredBy = referredBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLastLogin(@Nullable Instant lastLogin) { this.lastLogin = lastLogin; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(UserStatus status) { this.status = status.name(); }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setTwoFactorEnabled(Boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }
    
    // Domain methods
    public void recordLogin() {
        this.lastLogin = Instant.now();
    }
    
    public void addBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    
    public void deductBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance = this.balance.subtract(amount);
    }
    
    public boolean hasApiAccess() {
        return this.apiKey != null && !this.apiKey.isBlank();
    }
    
    public boolean isActive() {
        return UserStatus.ACTIVE.name().equals(this.status);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserEntity that = (UserEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "UserEntity{" +
            "id=" + id +
            ", email='" + email + '\'' +
            ", tier='" + tier + '\'' +
            ", balance=" + balance +
            ", status='" + status + '\'' +
            ", createdAt=" + createdAt +
            '}';
    }
    
    // Builder
    public static class Builder {
        private UUID id;
        private String email;
        private String passwordHash;
        private String tier;
        private BigDecimal balance;
        private String apiKey;
        private String webhookUrl;
        private String discordWebhook;
        private String companyName;
        private String referralCode;
        private UUID referredBy;
        private Instant lastLogin;
        private String status;
        private Boolean emailVerified;
        private Boolean twoFactorEnabled;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder tier(String tier) { this.tier = tier; return this; }
        public Builder tier(UserTier tier) { this.tier = tier.name(); return this; }
        public Builder balance(BigDecimal balance) { this.balance = balance; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder discordWebhook(String discordWebhook) { this.discordWebhook = discordWebhook; return this; }
        public Builder companyName(String companyName) { this.companyName = companyName; return this; }
        public Builder referralCode(String referralCode) { this.referralCode = referralCode; return this; }
        public Builder referredBy(UUID referredBy) { this.referredBy = referredBy; return this; }
        public Builder lastLogin(Instant lastLogin) { this.lastLogin = lastLogin; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder status(UserStatus status) { this.status = status.name(); return this; }
        public Builder emailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public Builder twoFactorEnabled(Boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; return this; }
        
        public UserEntity build() {
            return new UserEntity(this);
        }
    }
}
