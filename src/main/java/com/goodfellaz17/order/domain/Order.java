package com.goodfellaz17.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.*;

/**
 * Order entity: represents a user's request to deliver plays to a track.
 * Lifecycle: PENDING → ACTIVE → DELIVERING → COMPLETED (or FAILED)
 * Table: pipeline_orders (separate from the main orders table for global order system)
 */
@Table(name = "pipeline_orders")
public class Order {

    @Id
    private UUID id;

    @Column(value = "track_id")
    private String trackId;         // Spotify track ID being promoted

    @Column(value = "quantity")
    private Integer quantity;       // How many plays to deliver

    @Column(value = "status")
    private String status;     // Current lifecycle state (String for R2DBC)

    @Column(value = "plays_delivered")
    private Integer playsDelivered = 0;  // Count of successful deliveries

    @Column(value = "plays_failed")
    private Integer playsFailed = 0;     // Count of failed attempts

    @Column(value = "failure_reason")
    private String failureReason;   // Why order failed (if applicable)

    @Column(value = "created_at")
    private Instant createdAt;

    @Column(value = "completed_at")
    private Instant completedAt;

    @Column(value = "last_updated_at")
    private Instant lastUpdatedAt;

    // Relationships - TRANSIENT for R2DBC (tasks loaded separately)
    @org.springframework.data.annotation.Transient
    private Set<OrderTask> tasks = new HashSet<>();

    // Constructor for R2DBC - initializes defaults (but not UUID, let db or caller generate it)
    public Order() {
        this.createdAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
        this.status = "PENDING";
        this.playsDelivered = 0;
        this.playsFailed = 0;
    }

// Getters
    public UUID getId() { return id; }
    public String getTrackId() { return trackId; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public Integer getPlaysDelivered() { return playsDelivered; }
    public Integer getPlaysFailed() { return playsFailed; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public Set<OrderTask> getTasks() { return tasks; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setTrackId(String trackId) { this.trackId = trackId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setStatus(String status) { this.status = status; }
    public void setPlaysDelivered(Integer count) { this.playsDelivered = count; }
    public void setPlaysFailed(Integer count) { this.playsFailed = count; }
    public void setFailureReason(String reason) { this.failureReason = reason; }
    public void setCreatedAt(Instant time) { this.createdAt = time; }
    public void setCompletedAt(Instant time) { this.completedAt = time; }
    public void setLastUpdatedAt(Instant time) { this.lastUpdatedAt = time; }

    // Business Logic
    public void recordSuccess() {
        this.playsDelivered++;
    }

    public void recordFailure(String reason) {
        this.playsFailed++;
        this.failureReason = reason;
    }

    public boolean isComplete() {
        return status.equals("COMPLETED") || status.equals("FAILED");
    }

    public double getSuccessRate() {
        int total = playsDelivered + playsFailed;
        if (total == 0) return 0.0;
        return (double) playsDelivered / total;
    }
}
