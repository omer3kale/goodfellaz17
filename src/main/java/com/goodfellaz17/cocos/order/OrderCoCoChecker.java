package com.goodfellaz17.cocos.order;

import com.goodfellaz17.cocos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Order CoCo Checker - Validates all order context conditions.
 * 
 * Manual Ch.10.3: CoCo checkers aggregate multiple CoCos.
 * OrderCoCoChecker runs all order-related validations.
 */
@Component
public class OrderCoCoChecker {
    
    private static final Logger log = LoggerFactory.getLogger(OrderCoCoChecker.class);
    
    private final List<CoCo<OrderContext>> cocos;
    
    public OrderCoCoChecker() {
        this.cocos = new ArrayList<>();
        
        // Register all order CoCos
        cocos.add(new TrackUrlValidCoCo());
        cocos.add(new OrderQuantityValidCoCo());
        cocos.add(new ApiKeyBalanceCoCo());
        cocos.add(new SpotifyDripRateCoCo());
        
        log.info("OrderCoCoChecker initialized with {} CoCos", cocos.size());
    }
    
    /**
     * Check all CoCos and throw on first violation.
     * 
     * @param context Order context to validate
     * @throws CoCoViolationException if any CoCo fails
     */
    public void checkAll(OrderContext context) throws CoCoViolationException {
        CoCoCollector.clear();
        
        for (CoCo<OrderContext> coco : cocos) {
            try {
                coco.check(context);
            } catch (CoCoViolationException e) {
                log.warn("CoCo violation: {} - {}", coco.getErrorCode(), e.getMessage());
                throw e;
            }
        }
        
        log.debug("All {} CoCos passed for order", cocos.size());
    }
    
    /**
     * Check all CoCos and collect all violations.
     * Does not throw - caller should check CoCoCollector.hasErrors().
     * 
     * @param context Order context to validate
     */
    public void checkAllCollecting(OrderContext context) {
        CoCoCollector.clear();
        
        for (CoCo<OrderContext> coco : cocos) {
            try {
                coco.check(context);
            } catch (CoCoViolationException e) {
                CoCoCollector.addError(e);
            }
        }
        
        if (CoCoCollector.hasErrors()) {
            log.warn("CoCo validation found {} errors", CoCoCollector.getErrorCount());
        }
    }
    
    /**
     * Check a specific CoCo.
     */
    public void check(CoCo<OrderContext> coco, OrderContext context) throws CoCoViolationException {
        coco.check(context);
    }
    
    /**
     * Add a custom CoCo to the checker.
     */
    public void addCoCo(CoCo<OrderContext> coco) {
        cocos.add(coco);
        log.info("Added CoCo: {}", coco.getDescription());
    }
    
    /**
     * Get the number of registered CoCos.
     */
    public int getCoCoCount() {
        return cocos.size();
    }
    
    /**
     * Get all registered CoCos.
     */
    public List<CoCo<OrderContext>> getCocos() {
        return List.copyOf(cocos);
    }
}
