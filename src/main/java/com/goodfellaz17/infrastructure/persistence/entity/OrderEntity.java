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
 */
@Table("orders")
public class OrderEntity {

    @Id
    private UUID id;

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

    @Column("progress")
    private Integer progress;

    @Column("delivered_quantity")
    private Integer deliveredQuantity;

    @Column("started_at")
    private Instant startedAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("refundable")
    private Boolean refundable;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private Instant createdAt;

    // Default constructor for R2DBC
    public OrderEntity() {}

    public OrderEntity(String apiKey, Integer serviceId, String link, 
                       Integer quantity, BigDecimal charged, String status) {
        this.id = UUID.randomUUID();
        this.apiKey = apiKey;
        this.serviceId = serviceId;
        this.link = link;
        this.quantity = quantity;
        this.charged = charged;
        this.status = status;
        this.progress = 0;
        this.deliveredQuantity = 0;
        this.refundable = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public Integer getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(Integer deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Boolean getRefundable() { return refundable; }
    public void setRefundable(Boolean refundable) { this.refundable = refundable; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
