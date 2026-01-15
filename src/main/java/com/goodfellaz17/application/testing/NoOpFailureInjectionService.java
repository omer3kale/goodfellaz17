package com.goodfellaz17.application.testing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * NoOpFailureInjectionService - Production stub that does nothing.
 * 
 * Ensures failure injection is completely disabled in production
 * even if someone tries to inject the service.
 * 
 * @author goodfellaz17
 * @since 1.0.0
 */
@Service
@Profile({"prod", "production"})
public class NoOpFailureInjectionService extends FailureInjectionService {
    
    private static final Logger log = LoggerFactory.getLogger(NoOpFailureInjectionService.class);
    
    public NoOpFailureInjectionService() {
        super();
        log.info("✅ NoOpFailureInjectionService loaded - failure injection disabled in production");
    }
    
    @Override
    public void enable() {
        log.warn("FAILURE_INJECTION_BLOCKED | Cannot enable in production profile");
    }
    
    @Override
    public boolean isEnabled() {
        return false; // Always disabled
    }
    
    @Override
    public boolean shouldInjectFailure() {
        return false;
    }
    
    @Override
    public boolean shouldInjectTimeout() {
        return false;
    }
    
    @Override
    public boolean isProxyBanned(java.util.UUID proxyId) {
        return false;
    }
}
