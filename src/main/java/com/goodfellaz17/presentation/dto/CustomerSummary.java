package com.goodfellaz17.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer Summary DTO - Dashboard header data.
 * Matches botzzz773.pro exact field names.
 */
public record CustomerSummary(
    String apiKey,
    String userName,
    BigDecimal balance,
    long activeOrders,
    BigDecimal totalSpent,
    List<OrderSummary> recentOrders
) {
    /**
     * Create from individual components.
     */
    public static CustomerSummary of(String apiKey, String userName, BigDecimal balance, 
                                      long activeOrders, BigDecimal totalSpent, 
                                      List<OrderSummary> recentOrders) {
        return new CustomerSummary(apiKey, userName, balance, activeOrders, totalSpent, recentOrders);
    }
}
