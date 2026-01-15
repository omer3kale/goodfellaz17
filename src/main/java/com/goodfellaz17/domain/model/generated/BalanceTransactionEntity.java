package com.goodfellaz17.domain.model.generated;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
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
 * Entity: BalanceTransaction
 * Table: balance_transactions
 * 
 * Financial ledger for all balance changes (debits, credits, refunds).
 * Maintains audit trail with before/after balances.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
@Table("balance_transactions")
public class BalanceTransactionEntity implements Serializable, Persistable<UUID> {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @NotNull
    private UUID id;
    
    @NotNull
    @Column("user_id")
    private UUID userId;
    
    @Nullable
    @Column("order_id")
    private UUID orderId;
    
    @NotNull
    @Column("amount")
    private BigDecimal amount;
    
    @NotNull
    @Column("balance_before")
    private BigDecimal balanceBefore;
    
    @NotNull
    @Column("balance_after")
    private BigDecimal balanceAfter;
    
    @NotNull
    @Column("type")
    private String type;
    
    @NotNull
    @Size(max = 500)
    @Column("reason")
    private String reason;
    
    @Nullable
    @Size(max = 50)
    @Column("payment_provider")
    private String paymentProvider;
    
    @Nullable
    @Size(max = 128)
    @Column("external_tx_id")
    private String externalTxId;
    
    @NotNull
    @Column("timestamp")
    private Instant timestamp = Instant.now();
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public boolean isNew() {
        return isNew;
    }
    
    /**
     * Mark entity as not new (for updates).
     */
    public BalanceTransactionEntity markNotNew() {
        this.isNew = false;
        return this;
    }
    
    public BalanceTransactionEntity() {
        this.id = UUID.randomUUID();
        this.timestamp = Instant.now();
        this.isNew = true;
    }
    
    private BalanceTransactionEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.userId = Objects.requireNonNull(builder.userId);
        this.orderId = builder.orderId;
        this.amount = Objects.requireNonNull(builder.amount);
        this.balanceBefore = Objects.requireNonNull(builder.balanceBefore);
        this.balanceAfter = Objects.requireNonNull(builder.balanceAfter);
        this.type = Objects.requireNonNull(builder.type);
        this.reason = Objects.requireNonNull(builder.reason);
        this.paymentProvider = builder.paymentProvider;
        this.externalTxId = builder.externalTxId;
        this.timestamp = Instant.now();
        this.isNew = true;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a debit transaction for an order.
     */
    public static BalanceTransactionEntity createDebit(
        UUID userId, UUID orderId, BigDecimal amount, 
        BigDecimal balanceBefore, String reason
    ) {
        return builder()
            .userId(userId)
            .orderId(orderId)
            .amount(amount.negate())
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceBefore.subtract(amount))
            .type(TransactionType.DEBIT)
            .reason(reason)
            .build();
    }
    
    /**
     * Create a credit transaction for a deposit.
     */
    public static BalanceTransactionEntity createCredit(
        UUID userId, BigDecimal amount, BigDecimal balanceBefore, 
        String reason, String paymentProvider, String externalTxId
    ) {
        return builder()
            .userId(userId)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceBefore.add(amount))
            .type(TransactionType.CREDIT)
            .reason(reason)
            .paymentProvider(paymentProvider)
            .externalTxId(externalTxId)
            .build();
    }
    
    /**
     * Create a refund transaction.
     */
    public static BalanceTransactionEntity createRefund(
        UUID userId, UUID orderId, BigDecimal amount, 
        BigDecimal balanceBefore, String reason
    ) {
        return builder()
            .userId(userId)
            .orderId(orderId)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceBefore.add(amount))
            .type(TransactionType.REFUND)
            .reason(reason)
            .build();
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    @Nullable public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getType() { return type; }
    public TransactionType getTypeEnum() { return TransactionType.valueOf(type); }
    public String getReason() { return reason; }
    @Nullable public String getPaymentProvider() { return paymentProvider; }
    @Nullable public String getExternalTxId() { return externalTxId; }
    public Instant getTimestamp() { return timestamp; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setOrderId(@Nullable UUID orderId) { this.orderId = orderId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setBalanceBefore(BigDecimal balanceBefore) { this.balanceBefore = balanceBefore; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public void setType(String type) { this.type = type; }
    public void setType(TransactionType type) { this.type = type.name(); }
    public void setReason(String reason) { this.reason = reason; }
    public void setPaymentProvider(@Nullable String paymentProvider) { this.paymentProvider = paymentProvider; }
    public void setExternalTxId(@Nullable String externalTxId) { this.externalTxId = externalTxId; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    // Domain methods
    
    /**
     * Check if this is an incoming (positive) transaction.
     */
    public boolean isIncoming() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is an outgoing (negative) transaction.
     */
    public boolean isOutgoing() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Get absolute amount (always positive).
     */
    public BigDecimal getAbsoluteAmount() {
        return amount.abs();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BalanceTransactionEntity that = (BalanceTransactionEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "BalanceTransactionEntity{" +
            "id=" + id +
            ", userId=" + userId +
            ", amount=" + amount +
            ", type='" + type + '\'' +
            ", reason='" + reason + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }
    
    public static class Builder {
        private UUID id;
        private UUID userId;
        private UUID orderId;
        private BigDecimal amount;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private String type;
        private String reason;
        private String paymentProvider;
        private String externalTxId;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder orderId(UUID orderId) { this.orderId = orderId; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder balanceBefore(BigDecimal balanceBefore) { this.balanceBefore = balanceBefore; return this; }
        public Builder balanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder type(TransactionType type) { this.type = type.name(); return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder paymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; return this; }
        public Builder externalTxId(String externalTxId) { this.externalTxId = externalTxId; return this; }
        
        public BalanceTransactionEntity build() {
            return new BalanceTransactionEntity(this);
        }
    }
}
