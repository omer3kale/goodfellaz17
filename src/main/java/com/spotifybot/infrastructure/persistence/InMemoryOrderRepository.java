package com.spotifybot.infrastructure.persistence;

import com.spotifybot.domain.model.Order;
import com.spotifybot.domain.model.OrderStatus;
import com.spotifybot.domain.port.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infrastructure Adapter - In-Memory Order Repository.
 * 
 * Development implementation of OrderRepositoryPort.
 * Production: Replace with SupabaseOrderRepositoryAdapter.
 */
@Repository
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryOrderRepository.class);

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        log.debug("Order saved: id={}, status={}", order.getId(), order.getStatus());
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findPendingOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .toList();
    }

    @Override
    public List<Order> findProcessingWithRemaining() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.PROCESSING)
                .filter(o -> o.getDelivered() < o.getQuantity())
                .toList();
    }

    @Override
    public void updateDelivered(UUID orderId, int delivered) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.addDelivered(delivered);
            log.debug("Order updated: id={}, delivered={}/{}", 
                    orderId, order.getDelivered(), order.getQuantity());
        }
    }
}
