package com.suko.zeroparse;

/**
 * A JSON boolean value.
 * 
 * <p>This class represents JSON boolean values (true/false) using
 * singleton instances for efficiency.</p>
 */
public final class JsonBoolean implements JsonValue {
    
    /** The true value singleton */
    public static final JsonBoolean TRUE = new JsonBoolean(true);
    
    /** The false value singleton */
    public static final JsonBoolean FALSE = new JsonBoolean(false);
    
    private final boolean value;
    
    private JsonBoolean(boolean value) {
        this.value = value;
    }
    
    /**
     * Get a JsonBoolean instance for the given boolean value.
     * 
     * @param value the boolean value
     * @return TRUE or FALSE singleton
     */
    public static JsonBoolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }
    
    @Override
    public JsonType getType() {
        return JsonType.BOOLEAN;
    }
    
    /**
     * Get the boolean value.
     * 
     * @return the boolean value
     */
    public boolean booleanValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return value == ((JsonBoolean) obj).value;
    }
    
    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }
    
    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
