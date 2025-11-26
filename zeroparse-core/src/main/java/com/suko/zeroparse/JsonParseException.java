package com.suko.zeroparse;

/**
 * Exception thrown when JSON parsing fails.
 * 
 * <p>This exception provides detailed information about parsing errors,
 * including the position where the error occurred and context about
 * what was expected.</p>
 */
public class JsonParseException extends RuntimeException {
    
    private final int offset;
    private final String context;
    
    /**
     * Create a new JsonParseException.
     * 
     * @param message the error message
     * @param offset the position where the error occurred
     */
    public JsonParseException(String message, int offset) {
        super(message);
        this.offset = offset;
        this.context = null;
    }
    
    /**
     * Create a new JsonParseException with context.
     * 
     * @param message the error message
     * @param offset the position where the error occurred
     * @param context additional context about the error
     */
    public JsonParseException(String message, int offset, String context) {
        super(message);
        this.offset = offset;
        this.context = context;
    }
    
    /**
     * Create a new JsonParseException with cause.
     * 
     * @param message the error message
     * @param offset the position where the error occurred
     * @param cause the underlying cause
     */
    public JsonParseException(String message, int offset, Throwable cause) {
        super(message, cause);
        this.offset = offset;
        this.context = null;
    }
    
    /**
     * Create a new JsonParseException with context and cause.
     * 
     * @param message the error message
     * @param offset the position where the error occurred
     * @param context additional context about the error
     * @param cause the underlying cause
     */
    public JsonParseException(String message, int offset, String context, Throwable cause) {
        super(message, cause);
        this.offset = offset;
        this.context = context;
    }
    
    /**
     * Get the position where the error occurred.
     * 
     * @return the offset
     */
    public int getOffset() {
        return offset;
    }
    
    /**
     * Get additional context about the error.
     * 
     * @return the context, or null if not available
     */
    public String getContext() {
        return context;
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (context != null) {
            return baseMessage + " at offset " + offset + " (" + context + ")";
        } else {
            return baseMessage + " at offset " + offset;
        }
    }
}
