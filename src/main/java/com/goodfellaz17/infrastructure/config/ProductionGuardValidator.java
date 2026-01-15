package com.goodfellaz17.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * ProductionGuardValidator - Validates critical production settings on startup.
 * 
 * ONLY runs in production profile. Fails fast if unsafe settings are detected.
 * 
 * Guards:
 *   1. Time multiplier MUST be 1 (no compression)
 *   2. Chaos injection MUST be blocked
 *   3. Worker interval MUST be >= 5000ms
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Component
@Profile({"prod", "production"})
public class ProductionGuardValidator {
    
    private static final Logger log = LoggerFactory.getLogger(ProductionGuardValidator.class);
    
    @Value("${goodfellaz17.delivery.time-multiplier:1}")
    private int timeMultiplier;
    
    @Value("${goodfellaz17.chaos.available:false}")
    private boolean chaosAvailable;
    
    @Value("${goodfellaz17.worker.interval-ms:10000}")
    private int workerIntervalMs;
    
    @Value("${goodfellaz17.production-guards.enforce-real-timing:true}")
    private boolean enforceRealTiming;
    
    @Value("${goodfellaz17.production-guards.block-chaos-injection:true}")
    private boolean blockChaosInjection;
    
    @PostConstruct
    public void validateProductionGuards() {
        log.info("══════════════════════════════════════════════════════════════");
        log.info("  PRODUCTION GUARD VALIDATION");
        log.info("══════════════════════════════════════════════════════════════");
        
        boolean allPassed = true;
        
        // Guard 1: Time multiplier must be 1
        if (enforceRealTiming && timeMultiplier != 1) {
            log.error("❌ GUARD VIOLATION: time-multiplier={} (MUST be 1 in production!)", timeMultiplier);
            allPassed = false;
        } else {
            log.info("✅ Time multiplier: {} (real timing enforced)", timeMultiplier);
        }
        
        // Guard 2: Chaos injection must be blocked
        if (blockChaosInjection && chaosAvailable) {
            log.error("❌ GUARD VIOLATION: chaos.available=true (MUST be false in production!)");
            allPassed = false;
        } else {
            log.info("✅ Chaos injection: BLOCKED");
        }
        
        // Guard 3: Worker interval must be reasonable
        if (workerIntervalMs < 5000) {
            log.error("❌ GUARD VIOLATION: worker.interval-ms={} (MUST be >= 5000 in production!)", workerIntervalMs);
            allPassed = false;
        } else {
            log.info("✅ Worker interval: {}ms", workerIntervalMs);
        }
        
        log.info("══════════════════════════════════════════════════════════════");
        
        if (!allPassed) {
            log.error("PRODUCTION GUARD VALIDATION FAILED! Refusing to start with unsafe settings.");
            throw new IllegalStateException(
                "Production guard validation failed! Review application-prod.yml settings."
            );
        }
        
        log.info("  ALL PRODUCTION GUARDS PASSED ✓");
        log.info("══════════════════════════════════════════════════════════════");
    }
}
