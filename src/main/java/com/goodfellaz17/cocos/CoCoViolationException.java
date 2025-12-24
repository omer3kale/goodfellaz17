package com.goodfellaz17.cocos;

/**
 * Exception thrown when a CoCo validation fails.
 * 
 * Manual Ch.10.2: Log.error() reports CoCo violations.
 * CoCoViolationException provides structured error information.
 */
public class CoCoViolationException extends RuntimeException {
    
    private final String errorCode;
    private final String field;
    private final Object invalidValue;
    
    public CoCoViolationException(String errorCode, String message) {
        this(errorCode, message, null, null);
    }
    
    public CoCoViolationException(String errorCode, String message, String field, Object invalidValue) {
        super(formatMessage(errorCode, message, field, invalidValue));
        this.errorCode = errorCode;
        this.field = field;
        this.invalidValue = invalidValue;
    }
    
    private static String formatMessage(String errorCode, String message, String field, Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append(errorCode).append(": ").append(message);
        if (field != null) {
            sb.append(" [field=").append(field);
            if (value != null) {
                sb.append(", value=").append(value);
            }
            sb.append("]");
        }
        return sb.toString();
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getField() {
        return field;
    }
    
    public Object getInvalidValue() {
        return invalidValue;
    }
    
    @Override
    public String toString() {
        return getMessage();
    }
}
