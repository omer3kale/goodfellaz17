package com.goodfellaz17.cocos;

import com.goodfellaz17.cocos.order.OrderContext;
import com.goodfellaz17.symboltable.ApiKeySymbol;
import com.goodfellaz17.symboltable.ServiceSymbol;

import java.math.BigDecimal;

/**
 * CoCo: API key must have sufficient balance for order.
 * 
 * Manual Ch.10.8: Symbol table resolution in CoCos.
 * Validates balance before order acceptance.
 */
public class ApiKeyBalanceCoCo implements CoCo<OrderContext> {
    
    public static final String ERROR_CODE = "0xGFL03";
    
    @Override
    public void check(OrderContext context) throws CoCoViolationException {
        ApiKeySymbol apiKey = context.apiKey();
        ServiceSymbol service = context.service();
        int quantity = context.quantity();
        
        // Calculate order cost
        BigDecimal cost = service.calculateCost(quantity);
        
        if (!apiKey.hasSufficientBalance(cost)) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    String.format("Insufficient balance: $%.2f required, $%.2f available",
                            cost, apiKey.getBalance()),
                    "balance",
                    apiKey.getBalance()
            );
        }
    }
    
    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
    
    @Override
    public String getDescription() {
        return "API key must have sufficient balance for order";
    }
}
