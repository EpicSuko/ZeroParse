package com.suko.zeroparse;

/**
 * Base interface for all JSON values.
 * 
 * <p>This is the root of the JSON value hierarchy, providing common
 * type checking and conversion methods. All JSON values are immutable
 * and thread-safe.</p>
 */
public interface JsonValue {
    
    /**
     * Get the JSON value type.
     * 
     * @return the type of this JSON value
     */
    JsonType getType();
    
    /**
     * Check if this value is a JSON object.
     * 
     * @return true if this is a JsonObject
     */
    default boolean isObject() {
        return getType() == JsonType.OBJECT;
    }
    
    /**
     * Check if this value is a JSON array.
     * 
     * @return true if this is a JsonArray
     */
    default boolean isArray() {
        return getType() == JsonType.ARRAY;
    }
    
    /**
     * Check if this value is a JSON string.
     * 
     * @return true if this is a JsonStringView
     */
    default boolean isString() {
        return getType() == JsonType.STRING;
    }
    
    /**
     * Check if this value is a JSON number.
     * 
     * @return true if this is a JsonNumberView
     */
    default boolean isNumber() {
        return getType() == JsonType.NUMBER;
    }
    
    /**
     * Check if this value is a JSON boolean.
     * 
     * @return true if this is a JsonBoolean
     */
    default boolean isBoolean() {
        return getType() == JsonType.BOOLEAN;
    }
    
    /**
     * Check if this value is JSON null.
     * 
     * @return true if this is JsonNull
     */
    default boolean isNull() {
        return getType() == JsonType.NULL;
    }
    
    /**
     * Cast this value to a JsonObject.
     * 
     * @return this value as a JsonObject
     * @throws ClassCastException if this is not an object
     */
    default JsonObject asObject() {
        if (!isObject()) {
            throw new ClassCastException("Not a JSON object: " + getType());
        }
        return (JsonObject) this;
    }
    
    /**
     * Cast this value to a JsonArray.
     * 
     * @return this value as a JsonArray
     * @throws ClassCastException if this is not an array
     */
    default JsonArray asArray() {
        if (!isArray()) {
            throw new ClassCastException("Not a JSON array: " + getType());
        }
        return (JsonArray) this;
    }
    
    /**
     * Cast this value to a JsonStringView.
     * 
     * @return this value as a JsonStringView
     * @throws ClassCastException if this is not a string
     */
    default JsonStringView asString() {
        if (!isString()) {
            throw new ClassCastException("Not a JSON string: " + getType());
        }
        return (JsonStringView) this;
    }
    
    /**
     * Cast this value to a JsonNumberView.
     * 
     * @return this value as a JsonNumberView
     * @throws ClassCastException if this is not a number
     */
    default JsonNumberView asNumber() {
        if (!isNumber()) {
            throw new ClassCastException("Not a JSON number: " + getType());
        }
        return (JsonNumberView) this;
    }
    
    /**
     * Cast this value to a JsonBoolean.
     * 
     * @return this value as a JsonBoolean
     * @throws ClassCastException if this is not a boolean
     */
    default JsonBoolean asBoolean() {
        if (!isBoolean()) {
            throw new ClassCastException("Not a JSON boolean: " + getType());
        }
        return (JsonBoolean) this;
    }
    
    /**
     * Get the string representation of this JSON value.
     * 
     * <p>For string values, this returns the decoded string.
     * For other values, this returns their JSON representation.</p>
     * 
     * @return the string representation
     */
    String toString();
}
