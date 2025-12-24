package com.spotifybot.application.service;

import com.spotifybot.domain.model.*;
import com.spotifybot.domain.port.BotExecutorPort;
import com.spotifybot.domain.port.OrderRepositoryPort;
import com.spotifybot.infrastructure.bot.PremiumAccountFarm;
import com.spotifybot.infrastructure.bot.ResidentialProxyPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application Service - Bot execution orchestration.
 * 
 * CyyBot 3.0 Production Features:
 * - Order queue with priority scheduling
 * - Bot task decomposition (drip scheduling)
 * - Concurrent execution with capacity limits
 * - Spotify compliance (5% hourly spike limit)
 * - Proxy/account rotation per task
 */
@Service
public class BotOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(BotOrchestratorService.class);

    private static final int MAX_CONCURRENT_TASKS = 100;

    private final Queue<Order> orderQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    private final OrderRepositoryPort orderRepository;
    private final BotExecutorPort botExecutor;
    private final ResidentialProxyPool proxyPool;
    private final PremiumAccountFarm accountFarm;

    public BotOrchestratorService(OrderRepositoryPort orderRepository,
                                   BotExecutorPort botExecutor,
                                   ResidentialProxyPool proxyPool,
                                   PremiumAccountFarm accountFarm) {
        this.orderRepository = orderRepository;
        this.botExecutor = botExecutor;
        this.proxyPool = proxyPool;
        this.accountFarm = accountFarm;
    }

    /**
     * Queue order for async bot execution.
     */
    public void queueOrder(Order order) {
        order.startProcessing();
        orderRepository.save(order);
        orderQueue.add(order);
        log.info("Order queued: id={}, queueSize={}", order.getId(), orderQueue.size());
    }

    /**
     * Scheduled delivery loop - processes queue every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000)
    public void orchestrateDelivery() {
        if (orderQueue.isEmpty()) return;
        
        log.debug("Orchestrating delivery: queueSize={}, activeTasks={}", 
                orderQueue.size(), activeTasks.get());
        
        processQueue();
    }

    /**
     * Process queued orders with concurrency control.
     */
    @Async("botExecutor")
    public void processQueue() {
        while (!orderQueue.isEmpty() && botExecutor.hasCapacity()) {
            Order order = orderQueue.poll();
            if (order != null && order.getStatus() == OrderStatus.PROCESSING) {
                
                // Decompose to bot task
                BotTask task = createBotTask(order);
                if (task != null) {
                    executeBotTask(order, task);
                } else {
                    // No resources available, re-queue
                    orderQueue.add(order);
                    break;
                }
            }
        }
    }

    /**
     * Create bot task with proxy and account assignment.
     */
    private BotTask createBotTask(Order order) {
        Proxy proxy = proxyPool.nextFor(order.getGeoTarget());
        if (proxy == null) {
            log.warn("No proxy available for geo: {}", order.getGeoTarget());
            return null;
        }

        PremiumAccount account = accountFarm.nextHealthyAccount(order.getGeoTarget())
                .orElse(null);
        if (account == null) {
            log.warn("No healthy account available for geo: {}", order.getGeoTarget());
            return null;
        }

        SessionProfile profile = SessionProfile.randomHuman();
        
        return BotTask.create(
                order.getId(),
                order.getTrackUrl(),
                proxy,
                account,
                profile
        );
    }

    /**
     * Execute bot task and update order progress.
     */
    private void executeBotTask(Order order, BotTask task) {
        activeTasks.incrementAndGet();

        botExecutor.execute(task)
                .thenAccept(plays -> {
                    if (plays > 0) {
                        order.addDelivered(plays);
                        orderRepository.save(order);
                        log.debug("Delivery progress: orderId={}, delivered={}/{}", 
                                order.getId(), order.getDelivered(), order.getQuantity());
                    }
                    
                    // Re-queue if not complete
                    if (order.getDelivered() < order.getQuantity() 
                            && order.getStatus() == OrderStatus.PROCESSING) {
                        orderQueue.add(order);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Task failed: orderId={}, error={}", order.getId(), ex.getMessage());
                    proxyPool.reportFailure(task.proxy());
                    orderQueue.add(order); // Retry
                    return null;
                })
                .whenComplete((v, ex) -> activeTasks.decrementAndGet());
    }

    /**
     * Get current execution statistics.
     */
    public ExecutionStats getStats() {
        List<Order> processing = orderRepository.findProcessingWithRemaining();
        
        return new ExecutionStats(
                orderQueue.size(),
                processing.size(),
                botExecutor.getActiveTaskCount(),
                botExecutor.hasCapacity(),
                proxyPool.healthyCount(),
                accountFarm.healthyCount()
        );
    }

    /**
     * Execution statistics record.
     */
    public record ExecutionStats(
            long pendingOrders,
            long processingOrders,
            int activeBotTasks,
            boolean hasCapacity,
            int healthyProxies,
            int healthyAccounts
    ) {}
}
