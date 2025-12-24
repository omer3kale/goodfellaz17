package com.goodfellaz17.cocos;

import com.goodfellaz17.cocos.order.OrderContext;
import com.goodfellaz17.symboltable.ServiceSymbol;

/**
 * CoCo: Order quantity must be within service limits.
 * 
 * Manual Ch.10.8: CoCos use symbol table for validation.
 * Resolves service symbol to check min/max constraints.
 */
public class OrderQuantityValidCoCo implements CoCo<OrderContext> {
    
    public static final String ERROR_CODE = "0xGFL01";
    
    @Override
    public void check(OrderContext context) throws CoCoViolationException {
        ServiceSymbol service = context.service();
        int quantity = context.quantity();
        
        if (quantity < service.getMinOrder()) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    String.format("Order quantity %d is below service minimum %d", 
                            quantity, service.getMinOrder()),
                    "quantity",
                    quantity
            );
        }
        
        if (quantity > service.getMaxOrder()) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    String.format("Order quantity %d exceeds service maximum %d", 
                            quantity, service.getMaxOrder()),
                    "quantity",
                    quantity
            );
        }
    }
    
    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
    
    @Override
    public String getDescription() {
        return "Order quantity must be within service min/max limits";
    }
}
