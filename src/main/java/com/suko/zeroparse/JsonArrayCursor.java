package com.suko.zeroparse;

/**
 * A cursor for streaming JSON array elements.
 * 
 * <p>This class provides efficient element-by-element access to JSON arrays
 * without parsing the entire array at once. It's useful for processing large
 * arrays or when you only need to access specific elements.</p>
 */
public final class JsonArrayCursor {
    
    private final JsonArray array;
    private int currentIndex;
    
    /**
     * Create a new JsonArrayCursor.
     * 
     * @param cursor the input cursor (unused but kept for API consistency)
     * @param array the parsed array
     */
    public JsonArrayCursor(InputCursor cursor, JsonArray array) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }
        if (array == null) {
            throw new IllegalArgumentException("Array cannot be null");
        }
        this.array = array;
        this.currentIndex = 0;
    }
    
    /**
     * Check if there are more elements to read.
     * 
     * @return true if there are more elements
     */
    public boolean hasNext() {
        return currentIndex < array.size();
    }
    
    /**
     * Get the next element without advancing the cursor.
     * 
     * @return the next element, or null if no more elements
     */
    public JsonValue peek() {
        if (!hasNext()) {
            return null;
        }
        return array.get(currentIndex);
    }
    
    /**
     * Get the next element and advance the cursor.
     * 
     * @return the next element
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonValue next() {
        if (!hasNext()) {
            throw new java.util.NoSuchElementException("No more elements");
        }
        return array.get(currentIndex++);
    }
    
    /**
     * Get the next element as a string view.
     * 
     * @return the next element as a string view, or null if not a string
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonStringView nextString() {
        JsonValue value = next();
        return value != null && value.isString() ? value.asString() : null;
    }
    
    /**
     * Get the next element as a number view.
     * 
     * @return the next element as a number view, or null if not a number
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonNumberView nextNumber() {
        JsonValue value = next();
        return value != null && value.isNumber() ? value.asNumber() : null;
    }
    
    /**
     * Get the next element as a boolean.
     * 
     * @return the next element as a boolean, or null if not a boolean
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonBoolean nextBoolean() {
        JsonValue value = next();
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }
    
    /**
     * Get the next element as an object.
     * 
     * @return the next element as an object, or null if not an object
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonObject nextObject() {
        JsonValue value = next();
        return value != null && value.isObject() ? value.asObject() : null;
    }
    
    /**
     * Get the next element as an array.
     * 
     * @return the next element as an array, or null if not an array
     * @throws java.util.NoSuchElementException if no more elements
     */
    public JsonArray nextArray() {
        JsonValue value = next();
        return value != null && value.isArray() ? value.asArray() : null;
    }
    
    /**
     * Skip the next element without reading it.
     * 
     * @throws java.util.NoSuchElementException if no more elements
     */
    public void skip() {
        if (!hasNext()) {
            throw new java.util.NoSuchElementException("No more elements");
        }
        currentIndex++;
    }
    
    /**
     * Get the current index in the array.
     * 
     * @return the current index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * Get the total number of elements in the array.
     * 
     * @return the array size
     */
    public int size() {
        return array.size();
    }
    
    /**
     * Reset the cursor to the beginning.
     */
    public void reset() {
        currentIndex = 0;
    }
    
    /**
     * Get the underlying array.
     * 
     * @return the array
     */
    public JsonArray getArray() {
        return array;
    }
}
