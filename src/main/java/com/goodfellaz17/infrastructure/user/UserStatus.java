package com.goodfellaz17.infrastructure.user;

/**
 * User Proxy Status in the arbitrage network.
 */
public enum UserStatus {
    
    /** User online and ready for tasks */
    AVAILABLE,
    
    /** User currently executing a task */
    BUSY,
    
    /** User disconnected or timed out */
    OFFLINE,
    
    /** User suspended (failed tasks, suspicious activity) */
    SUSPENDED
}
