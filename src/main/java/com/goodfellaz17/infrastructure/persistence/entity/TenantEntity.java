package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant Entity (MSSQL) — Maps to tenants table.
 * 
 * Represents a customer organization for multi-tenant isolation.
 */
@Table("tenants")
public class TenantEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("code")
    private String code;

    @Column("name")
    private String name;

    @Column("contact_email")
    private String contactEmail;

    @Column("active")
    private Boolean active;

    @Column("credit_balance")
    private BigDecimal creditBalance;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    // ── Constructors ────────────────────────────────────

    public TenantEntity() {
    }

    public TenantEntity(UUID id, String code, String name, Boolean active, 
                       BigDecimal creditBalance, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
        this.creditBalance = creditBalance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Getters & Setters ────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public BigDecimal getCreditBalance() {
        return creditBalance;
    }

    public void setCreditBalance(BigDecimal creditBalance) {
        this.creditBalance = creditBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "TenantEntity{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", creditBalance=" + creditBalance +
                ", createdAt=" + createdAt +
                '}';
    }
}
