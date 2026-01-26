package com.goodfellaz17.order.service;

import com.goodfellaz17.order.domain.Order;
import com.goodfellaz17.order.domain.OrderTask;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OrderTaskFactory: Decomposes an Order into individual OrderTask units.
 * One order → many tasks (one task per account to play from).
 */
@Service
public class OrderTaskFactory {

    /**
     * Create tasks for an order, given a list of account IDs.
     * For N accounts, creates N tasks (one play per account).
     *
     * @param order The order to decompose
     * @param accountIds The accounts to play from
     * @return Set of OrderTask objects
     */
    public Set<OrderTask> createTasksForOrder(Order order, List<String> accountIds) {
        Set<OrderTask> tasks = new HashSet<>();

        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("At least one account ID required to create tasks");
        }

        for (String accountId : accountIds) {
            OrderTask task = new OrderTask();
            task.setId(java.util.UUID.randomUUID());  // Generate UUID before save
            task.setOrderId(order.getId());
            task.setAccountId(accountId);
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setMaxRetries(3);
            task.setCreatedAt(java.time.Instant.now());

            tasks.add(task);
        }

        return tasks;
    }

    /**
     * Create a single task for an order.
     * Used when manually adding a task to an existing order.
     */
    public OrderTask createTaskForOrder(Order order, String accountId) {
        OrderTask task = new OrderTask();
        task.setId(java.util.UUID.randomUUID());  // Generate UUID before save
        task.setOrderId(order.getId());
        task.setAccountId(accountId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetries(3);
        task.setCreatedAt(java.time.Instant.now());
        return task;
    }

    /**
     * Validate the task count matches the order quantity.
     * Used for audit/verification.
     */
    public boolean validateTaskCount(Order order, int expectedTaskCount) {
        if (order.getTasks() == null) {
            return expectedTaskCount == 0;
        }
        return order.getTasks().size() == expectedTaskCount;
    }
}
