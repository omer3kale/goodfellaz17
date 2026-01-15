package com.goodfellaz17.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * R2DBC Transaction Configuration.
 * 
 * Configures reactive transaction management for atomic database operations.
 * Used by OrderExecutionService to ensure balance deduction, order creation,
 * and transaction logging happen atomically.
 */
@Configuration
public class R2dbcTransactionConfig {
    
    /**
     * Reactive transaction manager for R2DBC connections.
     */
    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
    
    /**
     * TransactionalOperator for programmatic transaction control.
     * 
     * Usage:
     * transactionalOperator.transactional(
     *     operation1.then(operation2).then(operation3)
     * );
     * 
     * All operations either commit together or rollback together.
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
