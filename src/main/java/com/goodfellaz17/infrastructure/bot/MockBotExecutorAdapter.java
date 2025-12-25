package com.goodfellaz17.infrastructure.bot;

import com.goodfellaz17.domain.model.BotTask;
import com.goodfellaz17.domain.model.SessionProfile;
import com.goodfellaz17.domain.port.BotExecutorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEV ONLY: Mock Bot Executor for testing.
 * 
 * Simulates play execution without real Spotify API calls.
 * Only active when spring.profiles.active=dev
 */
@Component
@Profile("dev")
public class MockBotExecutorAdapter implements BotExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(MockBotExecutorAdapter.class);

    @Value("${bot.executor.max-concurrent:100}")
    private int maxConcurrent;

    private final AtomicInteger activeTasks = new AtomicInteger(0);

    @Override
    @Async("botExecutor")
    public CompletableFuture<Integer> execute(BotTask task) {
        activeTasks.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🧪 DEV: Simulating play: orderId={}, track={}", 
                        task.orderId(), task.trackUrl());
                
                SessionProfile profile = task.sessionProfile();
                
                // Simulate play duration (capped for dev speed)
                long durationMs = Math.min(profile.playDuration().toMillis(), 500);
                Thread.sleep(durationMs);
                
                // Record account usage
                task.account().recordPlay();
                
                log.debug("🧪 DEV: Mock play completed: orderId={}", task.orderId());
                return profile.isRoyaltyEligible() ? 1 : 0;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            } catch (Exception e) {
                log.error("DEV: Mock execution error: {}", e.getMessage());
                return 0;
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    @Override
    public boolean hasCapacity() {
        return activeTasks.get() < maxConcurrent;
    }

    @Override
    public int getActiveTaskCount() {
        return activeTasks.get();
    }
}
