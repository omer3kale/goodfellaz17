package com.spotifybot.domain.port;

import com.spotifybot.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Port - Order Repository.
 * 
 * Hexagonal Architecture: Domain defines WHAT it needs,
 * Infrastructure provides HOW (Supabase adapter).
 */
public interface OrderRepositoryPort {
    
    Order save(Order order);
    
    Optional<Order> findById(UUID id);
    
    List<Order> findPendingOrders();
    
    List<Order> findProcessingWithRemaining();
    
    void updateDelivered(UUID orderId, int delivered);
}
