package com.goodfellaz17.domain.model.generated;

/**
 * Task execution states for granular order delivery.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
public enum TaskStatus {
    
    /** Waiting to be picked up by worker */
    PENDING,
    
    /** Currently being processed by a worker */
    EXECUTING,
    
    /** Successfully delivered plays */
    COMPLETED,
    
    /** Transient failure, will be retried */
    FAILED_RETRYING,
    
    /** After max retries, moved to dead-letter queue */
    FAILED_PERMANENT;
    
    /**
     * Check if this is a terminal state (no more processing).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED_PERMANENT;
    }
    
    /**
     * Check if this status counts as "active" (still being worked on).
     */
    public boolean isActive() {
        return this == PENDING || this == EXECUTING || this == FAILED_RETRYING;
    }
}
