package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;

/**
 * Zero-copy, pooled JSON parser for Java with FastPool and Vert.x Buffer support.
 * 
 * <p>This parser provides zero-allocation parsing on hot paths with lazy decoding
 * and pooled object management. It supports multiple input types and exposes
 * views/slices instead of String allocations where possible.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Parse from Vert.x Buffer
 * Buffer buffer = Buffer.buffer("{\"name\":\"value\"}");
 * JsonValue root = ZeroParse.parse(buffer);
 * 
 * // Parse from byte array
 * byte[] data = "{\"name\":\"value\"}".getBytes(StandardCharsets.UTF_8);
 * JsonValue root = ZeroParse.parse(data, 0, data.length);
 * 
 * // Parse from String
 * String json = "{\"name\":\"value\"}";
 * JsonValue root = ZeroParse.parse(json);
 * 
 * // Access with zero-copy views
 * JsonObject obj = root.asObject();
 * JsonStringView name = obj.getStringView("name");
 * Utf8Slice slice = name.slice(); // No allocation
 * String decoded = name.toString(); // Allocates only when needed
 * }</pre>
 */
public final class ZeroParse {
    
    private ZeroParse() {
        // Utility class
    }
    
    /**
     * Parse JSON from a Vert.x Buffer.
     * 
     * @param buffer the Buffer containing UTF-8 encoded JSON
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public static JsonValue parse(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        JsonParser parser = new JsonParser();
        return parser.parse(buffer);
    }
    
    /**
     * Parse JSON from a byte array.
     * 
     * @param data the byte array containing UTF-8 encoded JSON
     * @param offset the starting offset in the array
     * @param length the number of bytes to read
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public static JsonValue parse(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        JsonParser parser = new JsonParser();
        return parser.parse(data, offset, length);
    }
    
    /**
     * Parse JSON from a String.
     * 
     * @param json the JSON string
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public static JsonValue parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        JsonParser parser = new JsonParser();
        return parser.parse(json);
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param buffer the Buffer containing a JSON array
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public static JsonArrayCursor streamArray(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        JsonParser parser = new JsonParser();
        return parser.streamArray(buffer);
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param data the byte array containing a JSON array
     * @param offset the starting offset in the array
     * @param length the number of bytes to read
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public static JsonArrayCursor streamArray(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        JsonParser parser = new JsonParser();
        return parser.streamArray(data, offset, length);
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param json the JSON string containing an array
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public static JsonArrayCursor streamArray(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        JsonParser parser = new JsonParser();
        return parser.streamArray(json);
    }
}
