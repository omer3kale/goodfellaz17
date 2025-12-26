package com.goodfellaz17.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order Entity - Core tracking.
 * Maps to orders table for R2DBC.
 * Matches existing Neon schema exactly.
 */
@Table("orders")
public class OrderEntity {

    @Id
    @Column("order_id")
    private UUID orderId;

    @Column("api_key")
    private String apiKey;

    @Column("service_id")
    private Integer serviceId;

    @Column("link")
    private String link;

    @Column("quantity")
    private Integer quantity;

    @Column("charged")
    private BigDecimal charged;

    @Column("status")
    private String status;

    @Column("charged_count")
    private Integer chargedCount;

    @Column("remaining_count")
    private Integer remainingCount;

    @Column("start_date")
    private Instant startDate;

    @Column("eta_minutes")
    private Integer etaMinutes;

    @Column("proxy_pool")
    private String proxyPool;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    // Default constructor for R2DBC
    public OrderEntity() {}

    public OrderEntity(String apiKey, Integer serviceId, String link, 
                       Integer quantity, BigDecimal charged, String status) {
        this.orderId = UUID.randomUUID();
        this.apiKey = apiKey;
        this.serviceId = serviceId;
        this.link = link;
        this.quantity = quantity;
        this.charged = charged;
        this.status = status;
        this.chargedCount = 0;
        this.remainingCount = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getOrderId() { return orderId; }
    public UUID getId() { return orderId; }  // Alias for compatibility
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getCharged() { return charged; }
    public void setCharged(BigDecimal charged) { this.charged = charged; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getChargedCount() { return chargedCount; }
    public void setChargedCount(Integer chargedCount) { this.chargedCount = chargedCount; }

    public Integer getRemainingCount() { return remainingCount; }
    public void setRemainingCount(Integer remainingCount) { this.remainingCount = remainingCount; }

    public Instant getStartDate() { return startDate; }
    public Instant getStartedAt() { return startDate; }  // Alias
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Integer getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }

    public String getProxyPool() { return proxyPool; }
    public void setProxyPool(String proxyPool) { this.proxyPool = proxyPool; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Computed properties for dashboard compatibility
    public Integer getProgress() {
        if (quantity == null || quantity == 0) return 0;
        int delivered = chargedCount != null ? chargedCount : 0;
        return (delivered * 100) / quantity;
    }

    public Integer getDeliveredQuantity() {
        return chargedCount != null ? chargedCount : 0;
    }
}
