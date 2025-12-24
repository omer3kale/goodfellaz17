package com.goodfellaz17.cocos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MontiCore-inspired CoCo Collector.
 * 
 * Manual Ch.10.3: Collects all CoCo violations for reporting.
 * Thread-local storage ensures isolation in concurrent environments.
 */
public final class CoCoCollector {
    
    private static final Logger log = LoggerFactory.getLogger(CoCoCollector.class);
    
    private static final ThreadLocal<List<CoCoViolationException>> errors = 
            ThreadLocal.withInitial(ArrayList::new);
    
    private CoCoCollector() {
        // Utility class
    }
    
    /**
     * Add an error to the collector.
     */
    public static void addError(CoCoViolationException error) {
        errors.get().add(error);
        log.warn("CoCo violation: {}", error.getMessage());
    }
    
    /**
     * Add an error with details.
     */
    public static void addError(String errorCode, String message, String field, Object value) {
        addError(new CoCoViolationException(errorCode, message, field, value));
    }
    
    /**
     * Add a simple error.
     */
    public static void addError(String errorCode, String message) {
        addError(new CoCoViolationException(errorCode, message));
    }
    
    /**
     * Check if there are any errors.
     */
    public static boolean hasErrors() {
        return !errors.get().isEmpty();
    }
    
    /**
     * Get all collected errors.
     */
    public static List<CoCoViolationException> getErrors() {
        return Collections.unmodifiableList(new ArrayList<>(errors.get()));
    }
    
    /**
     * Get the number of errors.
     */
    public static int getErrorCount() {
        return errors.get().size();
    }
    
    /**
     * Clear all errors (call at start of validation).
     */
    public static void clear() {
        errors.get().clear();
    }
    
    /**
     * Throw if any errors were collected.
     */
    public static void throwIfErrors() throws CoCoViolationException {
        List<CoCoViolationException> collected = errors.get();
        if (!collected.isEmpty()) {
            CoCoViolationException first = collected.get(0);
            clear();
            throw first;
        }
    }
    
    /**
     * Get a summary of all errors.
     */
    public static String getSummary() {
        List<CoCoViolationException> collected = errors.get();
        if (collected.isEmpty()) {
            return "No CoCo violations";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("CoCo violations (").append(collected.size()).append("):\n");
        for (int i = 0; i < collected.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(collected.get(i).getMessage()).append("\n");
        }
        return sb.toString();
    }
}
