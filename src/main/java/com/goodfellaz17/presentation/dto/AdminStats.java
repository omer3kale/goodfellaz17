package com.goodfellaz17.presentation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Admin Stats DTO - Admin panel metrics.
 * Matches botzzz773.pro exact field names.
 */
public record AdminStats(
    BigDecimal totalRevenue,
    BigDecimal revenue30d,
    BigDecimal revenueToday,
    long ordersToday,
    long activeOrders,
    long completedOrders,
    double deliveryRate24h,  // Percentage completed within 24h
    List<ServiceStats> topServices,
    Map<String, BigDecimal> dailyRevenue  // Last 30 days: "2024-12-25" -> 1234.50
) {
    /**
     * Service statistics record.
     */
    public record ServiceStats(
        int serviceId,
        String serviceName,
        long orderCount,
        double percentage
    ) {}
}
