package com.goodfellaz17.application.service;

import com.goodfellaz17.domain.exception.NoCapacityException;
import com.goodfellaz17.domain.model.*;
import com.goodfellaz17.domain.port.BotExecutorPort;
import com.goodfellaz17.domain.port.OrderRepositoryPort;
import com.goodfellaz17.infrastructure.bot.PremiumAccountFarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production Bot Orchestrator with Hybrid Routing.
 * 
 * This replaces the legacy orchestrator with full routing engine integration.
 * Uses RoutingEngine → ProxyStrategy → ProxySources for intelligent routing.
 * 
 * Routing Priority (cost optimized):
 * 1. USER_ARBITRAGE - Free (users provide IPs + accounts)
 * 2. TOR - Free (open network)
 * 3. P2P - Low cost (peer network)
 * 4. AWS - Medium cost (datacenter)
 * 5. MOBILE - High cost (carrier IPs)
 * 
 * The system automatically falls back through sources on capacity exhaustion.
 */
@Service
@Primary
@Profile("!dev")
public class HybridBotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(HybridBotOrchestrator.class);

    private static final int MAX_CONCURRENT_TASKS = 100;

    private final Queue<Order> orderQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    private final OrderRepositoryPort orderRepository;
    private final BotExecutorPort botExecutor;
    private final RoutingEngine routingEngine;
    private final PremiumAccountFarm accountFarm;

    public HybridBotOrchestrator(
            OrderRepositoryPort orderRepository,
            BotExecutorPort botExecutor,
            RoutingEngine routingEngine,
            PremiumAccountFarm accountFarm) {
        this.orderRepository = orderRepository;
        this.botExecutor = botExecutor;
        this.routingEngine = routingEngine;
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
     * Process queued orders with hybrid routing.
     */
    @Async("botExecutor")
    public void processQueue() {
        while (!orderQueue.isEmpty() && activeTasks.get() < MAX_CONCURRENT_TASKS) {
            Order order = orderQueue.poll();
            if (order != null && order.getStatus() == OrderStatus.PROCESSING) {
                processOrder(order);
            }
        }
    }

    /**
     * Process single order through hybrid routing.
     */
    private void processOrder(Order order) {
        try {
            // Get proxy lease through routing engine
            ProxyLease lease = routingEngine.route(
                    order.getId().toString(),
                    order.getServiceId(),
                    order.getServiceName(),
                    order.getGeoTarget() != null ? order.getGeoTarget().name() : null,
                    order.getQuantity() - order.getDelivered()
            );
            
            // All routes go through standard bot executor
            executeBotTask(order, lease);
            
        } catch (NoCapacityException e) {
            log.warn("No proxy capacity for order {}: {}", order.getId(), e.getMessage());
            // Re-queue for retry
            orderQueue.add(order);
        }
    }

    /**
     * Execute via traditional bot executor (proxy path).
     */
    private void executeBotTask(Order order, ProxyLease lease) {
        activeTasks.incrementAndGet();
        
        // Get account for execution
        PremiumAccount account = accountFarm.nextHealthyAccount(order.getGeoTarget())
                .orElse(null);
        
        if (account == null) {
            log.warn("No healthy account for order {}", order.getId());
            routingEngine.releaseLease(lease);
            orderQueue.add(order);
            activeTasks.decrementAndGet();
            return;
        }
        
        SessionProfile profile = SessionProfile.randomHuman();
        
        // Convert ProxyLease to Proxy for BotTask
        Proxy proxy = Proxy.of(
            lease.host(), 
            lease.port(), 
            null, null, 
            GeoTarget.WORLDWIDE, 
            false
        );
        
        BotTask task = BotTask.create(
                order.getId(),
                order.getTrackUrl(),
                proxy,
                account,
                profile
        );
        
        botExecutor.execute(task)
                .thenAccept(plays -> {
                    if (plays > 0) {
                        order.addDelivered(plays);
                        orderRepository.save(order);
                        log.debug("Delivery progress: orderId={}, delivered={}/{}", 
                                order.getId(), order.getDelivered(), order.getQuantity());
                    }
                    
                    // Release proxy lease
                    routingEngine.releaseLease(lease);
                    
                    // Re-queue if not complete
                    if (order.getDelivered() < order.getQuantity() 
                            && order.getStatus() == OrderStatus.PROCESSING) {
                        orderQueue.add(order);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Task failed: orderId={}, error={}", order.getId(), ex.getMessage());
                    routingEngine.releaseLease(lease);
                    orderQueue.add(order);
                    return null;
                })
                .whenComplete((v, ex) -> activeTasks.decrementAndGet());
    }

    // === Metrics ===

    public int getQueueSize() {
        return orderQueue.size();
    }

    public int getActiveTasks() {
        return activeTasks.get();
    }
}
