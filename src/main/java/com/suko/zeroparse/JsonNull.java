package com.suko.zeroparse;

/**
 * A JSON null value.
 * 
 * <p>This class represents JSON null values using a singleton
 * instance for efficiency.</p>
 */
public final class JsonNull implements JsonValue {
    
    /** The null value singleton */
    public static final JsonNull INSTANCE = new JsonNull();
    
    private JsonNull() {
        // Singleton
    }
    
    @Override
    public JsonType getType() {
        return JsonType.NULL;
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof JsonNull;
    }
    
    @Override
    public int hashCode() {
        return 0;
    }
    
    @Override
    public String toString() {
        return "null";
    }
}
