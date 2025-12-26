package com.goodfellaz17.infrastructure.config;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.goodfellaz17.infrastructure.persistence.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

/**
 * Refund Scheduler - Auto-refund failed orders.
 * 
 * Runs every 5 minutes to check for failed orders and refund the charged amount.
 * Updates order status to 'Refunded' after processing.
 */
@Component
@EnableScheduling
public class RefundScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefundScheduler.class);

    private final OrderRepository orderRepository;
    private final ApiKeyRepository apiKeyRepository;

    public RefundScheduler(OrderRepository orderRepository, 
                          ApiKeyRepository apiKeyRepository) {
        this.orderRepository = orderRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Run every 5 minutes to process failed orders.
     * Failed orders → refund charged amount → mark as Refunded
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void processFailedOrders() {
        log.info("Starting auto-refund scheduler...");

        orderRepository.findRefundableFailures()
            .flatMap(this::refundOrder)
            .count()
            .subscribe(count -> {
                if (count > 0) {
                    log.info("Auto-refund completed: {} orders refunded", count);
                } else {
                    log.debug("Auto-refund: No failed orders to process");
                }
            });
    }

    /**
     * Refund a single failed order.
     */
    private Flux<OrderEntity> refundOrder(OrderEntity order) {
        BigDecimal refundAmount = order.getCharged();

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Order {} has no refundable amount", order.getOrderId());
            return Flux.empty();
        }

        log.info("Refunding ${} to {} for order {}", 
            refundAmount, order.getApiKey(), order.getOrderId());

        return apiKeyRepository.refund(order.getApiKey(), refundAmount)
            .flatMap(updated -> orderRepository.markRefunded(order.getOrderId()))
            .thenMany(Flux.just(order))
            .doOnNext(o -> log.info("Refunded order {} successfully", o.getOrderId()))
            .onErrorResume(e -> {
                log.error("Failed to refund order {}: {}", order.getOrderId(), e.getMessage());
                return Flux.empty();
            });
    }

    /**
     * Manual trigger for testing - process all failed orders now.
     */
    public void triggerManualRefund() {
        log.info("Manual refund trigger activated");
        processFailedOrders();
    }
}
