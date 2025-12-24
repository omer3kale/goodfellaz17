package com.goodfellaz17.infrastructure.user;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Task assigned to a user proxy for execution.
 * 
 * Sent via WebSocket to user's browser.
 * User executes → reports completion → earns commission.
 */
public record TaskAssignment(
    UUID taskId,
    UUID orderId,
    String trackUrl,
    int quantity,
    int durationSeconds,
    BigDecimal commission,
    long expiresAt
) {
    
    public static TaskAssignmentBuilder builder() {
        return new TaskAssignmentBuilder();
    }
    
    /**
     * Calculate commission (30% of order value).
     */
    public static BigDecimal calculateCommission(int quantity, BigDecimal ratePerThousand) {
        BigDecimal orderValue = ratePerThousand.multiply(BigDecimal.valueOf(quantity / 1000.0));
        return orderValue.multiply(BigDecimal.valueOf(0.30));
    }
    
    public static class TaskAssignmentBuilder {
        private UUID taskId;
        private UUID orderId;
        private String trackUrl;
        private int quantity;
        private int durationSeconds = 45;
        private BigDecimal commission;
        private long expiresAt;
        
        public TaskAssignmentBuilder taskId(UUID taskId) {
            this.taskId = taskId;
            return this;
        }
        
        public TaskAssignmentBuilder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }
        
        public TaskAssignmentBuilder trackUrl(String trackUrl) {
            this.trackUrl = trackUrl;
            return this;
        }
        
        public TaskAssignmentBuilder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public TaskAssignmentBuilder durationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }
        
        public TaskAssignmentBuilder commission(BigDecimal commission) {
            this.commission = commission;
            return this;
        }
        
        public TaskAssignmentBuilder expiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }
        
        public TaskAssignment build() {
            return new TaskAssignment(taskId, orderId, trackUrl, quantity, 
                    durationSeconds, commission, expiresAt);
        }
    }
}
