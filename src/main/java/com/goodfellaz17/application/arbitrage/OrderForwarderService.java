package com.goodfellaz17.application.arbitrage;

import com.goodfellaz17.domain.model.Order;
import com.goodfellaz17.domain.port.OrderRepositoryPort;
import com.goodfellaz17.infrastructure.user.TaskAssignment;
import com.goodfellaz17.infrastructure.user.UserProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service - Order Forwarder.
 * 
 * Forwards orders to botzzz773.pro users instead of local Chrome.
 * 
 * Revenue Model:
 * - Panel pays $1.00 per 1K plays
 * - User earns 30% commission ($0.30)
 * - You keep 70% ($0.70)
 * - Infrastructure cost: $0
 * 
 * Profit: 70% margins with zero infrastructure.
 */
@Service
@SuppressWarnings("unused") // orderRepo reserved for future task tracking
public class OrderForwarderService {

    private static final Logger log = LoggerFactory.getLogger(OrderForwarderService.class);

    private final BotzzzUserProxyPool userPool;
    private final OrderRepositoryPort orderRepo;

    @Value("${arbitrage.batch-size:100}")
    private int batchSize;

    @Value("${arbitrage.rate-per-thousand:0.50}")
    private double ratePerThousand;

    public OrderForwarderService(BotzzzUserProxyPool userPool, OrderRepositoryPort orderRepo) {
        this.userPool = userPool;
        this.orderRepo = orderRepo;
    }

    /**
     * Forward order to available user for execution.
     * 
     * @param order Order to forward
     * @return Number of plays assigned (0 if no users available)
     */
    public int forwardToUser(Order order) {
        Optional<UserProxy> availableUser = userPool.nextHealthyUser(order.getGeoTarget());
        
        if (availableUser.isEmpty()) {
            log.warn("No users available for geo: {}", order.getGeoTarget());
            return 0;
        }
        
        UserProxy user = availableUser.get();
        int quantity = Math.min(batchSize, order.getQuantity() - order.getDelivered());
        
        TaskAssignment task = TaskAssignment.builder()
                .taskId(UUID.randomUUID())
                .orderId(order.getId())
                .trackUrl(order.getTrackUrl())
                .quantity(quantity)
                .durationSeconds(45) // Royalty-eligible duration
                .commission(calculateCommission(quantity))
                .expiresAt(Instant.now().plusSeconds(300).toEpochMilli())
                .build();
        
        userPool.sendTask(user, task);
        
        log.info("Order forwarded: orderId={}, userId={}, quantity={}, commission={}", 
                order.getId(), user.getUserId(), quantity, task.commission());
        
        return quantity;
    }

    /**
     * Calculate user commission (30% of order value).
     */
    private BigDecimal calculateCommission(int quantity) {
        double orderValue = ratePerThousand * (quantity / 1000.0);
        return BigDecimal.valueOf(orderValue * 0.30);
    }

    /**
     * Handle task completion callback from user.
     */
    public void handleTaskCompletion(UUID taskId, String userId, int plays) {
        userPool.completeTask(taskId, userId, plays);
        
        // Update order delivered count
        // Note: taskId -> orderId mapping maintained in userPool
        log.info("Task completed: taskId={}, userId={}, plays={}", taskId, userId, plays);
    }

    /**
     * Check if user pool has capacity.
     */
    public boolean hasCapacity() {
        return userPool.getAvailableUserCount() > 0;
    }

    /**
     * Get available user count.
     */
    public int getAvailableUsers() {
        return userPool.getAvailableUserCount();
    }
}
