package com.goodfellaz17.order.metrics;

import java.time.Instant;

/**
 * DeliveryMetrics: real-time pipeline health snapshot.
 * Tracks: total orders, success rate, task distribution, failure patterns.
 */
public class DeliveryMetrics {

    private Long totalOrders;           // Total orders received
    private Long completedOrders;       // Orders successfully completed
    private Long failedOrders;          // Orders that failed
    private Long pendingOrders;         // Waiting to be processed
    private Long activeOrders;          // Currently processing

    private Integer totalTasks;         // Total tasks created
    private Integer completedTasks;     // Tasks that succeeded
    private Integer failedTasks;        // Tasks that failed
    private Integer assignedTasks;      // Assigned to proxy nodes

    private Double overallSuccessRate;  // (completedTasks / totalTasks)
    private Double avgRetries;          // Average retries per task

    private Long totalPlaysDelivered;   // Sum of all successful plays
    private Long totalPlaysFailed;      // Sum of all failed plays

    private Instant capturedAt;         // When this metric was captured
    private String pipelineStatus;      // "Healthy", "Degraded", "Critical"

    // Constructor
    public DeliveryMetrics() {
        this.totalOrders = 0L;
        this.completedOrders = 0L;
        this.failedOrders = 0L;
        this.pendingOrders = 0L;
        this.activeOrders = 0L;
        this.totalTasks = 0;
        this.completedTasks = 0;
        this.failedTasks = 0;
        this.assignedTasks = 0;
        this.overallSuccessRate = 0.0;
        this.avgRetries = 0.0;
        this.totalPlaysDelivered = 0L;
        this.totalPlaysFailed = 0L;
        this.capturedAt = Instant.now();
        this.pipelineStatus = "Healthy";
    }

    // Getters
    public Long getTotalOrders() { return totalOrders; }
    public Long getCompletedOrders() { return completedOrders; }
    public Long getFailedOrders() { return failedOrders; }
    public Long getPendingOrders() { return pendingOrders; }
    public Long getActiveOrders() { return activeOrders; }

    public Integer getTotalTasks() { return totalTasks; }
    public Integer getCompletedTasks() { return completedTasks; }
    public Integer getFailedTasks() { return failedTasks; }
    public Integer getAssignedTasks() { return assignedTasks; }

    public Double getOverallSuccessRate() { return overallSuccessRate; }
    public Double getAvgRetries() { return avgRetries; }

    public Long getTotalPlaysDelivered() { return totalPlaysDelivered; }
    public Long getTotalPlaysFailed() { return totalPlaysFailed; }

    public Instant getCapturedAt() { return capturedAt; }
    public String getPipelineStatus() { return pipelineStatus; }

    // Setters
    public void setTotalOrders(Long value) { this.totalOrders = value; }
    public void setCompletedOrders(Long value) { this.completedOrders = value; }
    public void setFailedOrders(Long value) { this.failedOrders = value; }
    public void setPendingOrders(Long value) { this.pendingOrders = value; }
    public void setActiveOrders(Long value) { this.activeOrders = value; }

    public void setTotalTasks(Integer value) { this.totalTasks = value; }
    public void setCompletedTasks(Integer value) { this.completedTasks = value; }
    public void setFailedTasks(Integer value) { this.failedTasks = value; }
    public void setAssignedTasks(Integer value) { this.assignedTasks = value; }

    public void setOverallSuccessRate(Double value) { this.overallSuccessRate = value; }
    public void setAvgRetries(Double value) { this.avgRetries = value; }

    public void setTotalPlaysDelivered(Long value) { this.totalPlaysDelivered = value; }
    public void setTotalPlaysFailed(Long value) { this.totalPlaysFailed = value; }

    public void setPipelineStatus(String status) { this.pipelineStatus = status; }

    // Business Logic
    public void calculateAndUpdateStatus() {
        // Determine pipeline health
        if (overallSuccessRate == null || overallSuccessRate < 0.5) {
            this.pipelineStatus = "Critical";
        } else if (overallSuccessRate < 0.8) {
            this.pipelineStatus = "Degraded";
        } else {
            this.pipelineStatus = "Healthy";
        }
    }

    public Long getPendingTasks() {
        return (long) (totalTasks - completedTasks - failedTasks - assignedTasks);
    }

    public Integer getExecutingTasks() {
        return (totalTasks - completedTasks - failedTasks - assignedTasks);
    }

    @Override
    public String toString() {
        return "DeliveryMetrics{" +
                "totalOrders=" + totalOrders +
                ", completedOrders=" + completedOrders +
                ", failedOrders=" + failedOrders +
                ", pipelineStatus='" + pipelineStatus + '\'' +
                ", overallSuccessRate=" + overallSuccessRate +
                ", totalPlaysDelivered=" + totalPlaysDelivered +
                ", capturedAt=" + capturedAt +
                '}';
    }
}
