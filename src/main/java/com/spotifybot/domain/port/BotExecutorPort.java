package com.spotifybot.domain.port;

import com.spotifybot.domain.model.BotTask;

import java.util.concurrent.CompletableFuture;

/**
 * Domain Port - Bot Executor.
 * 
 * Hexagonal Architecture: Domain defines execution contract,
 * Infrastructure implements Chrome automation.
 */
public interface BotExecutorPort {
    
    /**
     * Execute a single bot task (play track via Chrome).
     * 
     * @param task Bot task with proxy, account, and track info
     * @return CompletableFuture with number of successful plays
     */
    CompletableFuture<Integer> execute(BotTask task);
    
    /**
     * Check if executor has capacity for more tasks.
     */
    boolean hasCapacity();
    
    /**
     * Get current active task count.
     */
    int getActiveTaskCount();
}
