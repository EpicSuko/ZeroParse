package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import com.suko.zeroparse.stack.StackTokenizer;
import com.suko.zeroparse.stack.AstStore;

/**
 * A JSON parser that provides zero-copy parsing capabilities.
 * 
 * <p>This parser provides efficient parsing of JSON from various input sources,
 * using lazy evaluation and AST-backed views for minimal allocations.</p>
 * 
 * <p>Uses ThreadLocal pooling for StackTokenizer reuse to minimize allocations
 * in high-throughput scenarios.</p>
 */
public final class JsonParser {
    
    // ThreadLocal pool for StackTokenizer reuse (eliminates allocation overhead)
    private static final ThreadLocal<StackTokenizer> TOKENIZER_POOL = 
        ThreadLocal.withInitial(StackTokenizer::new);
    
    /**
     * Create a new JsonParser.
     */
    public JsonParser() {
        // Simple constructor - no configuration needed
    }
    
    /**
     * Parse JSON from a Vert.x Buffer.
     * 
     * @param buffer the Buffer containing UTF-8 encoded JSON
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public JsonValue parse(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        return parse(new BufferCursor(buffer));
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
    public JsonValue parse(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return parse(new ByteArrayCursor(data, offset, length));
    }
    
    /**
     * Parse JSON from a String.
     * 
     * @param json the JSON string
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public JsonValue parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        return parse(new StringCursor(json));
    }
    
    /**
     * Parse JSON from an InputCursor.
     * 
     * @param cursor the input cursor
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public JsonValue parse(InputCursor cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }
        
        if (cursor.isEmpty()) {
            throw new JsonParseException("Empty JSON input", 0);
        }
        
        return parseWithStack(cursor);
    }
    
    /**
     * Parse using stack-based tokenizer.
     */
    private JsonValue parseWithStack(InputCursor cursor) {
        try {
            // Reuse tokenizer from ThreadLocal pool
            StackTokenizer tokenizer = TOKENIZER_POOL.get();
            AstStore astStore = tokenizer.tokenize(cursor);
            
            int rootIndex = astStore.getRoot();
            byte rootType = astStore.getType(rootIndex);
            
            switch (rootType) {
                case AstStore.TYPE_OBJECT:
                    return new JsonObject(astStore, rootIndex, cursor);
                case AstStore.TYPE_ARRAY:
                    return new JsonArray(astStore, rootIndex, cursor);
                case AstStore.TYPE_STRING:
                    return new JsonStringView(astStore, rootIndex, cursor);
                case AstStore.TYPE_NUMBER:
                    return new JsonNumberView(astStore, rootIndex, cursor);
                case AstStore.TYPE_BOOLEAN_TRUE:
                    return JsonBoolean.TRUE;
                case AstStore.TYPE_BOOLEAN_FALSE:
                    return JsonBoolean.FALSE;
                case AstStore.TYPE_NULL:
                    return JsonNull.INSTANCE;
                default:
                    throw new JsonParseException("Unknown root type: " + rootType, 0);
            }
        } catch (Exception e) {
            if (e instanceof JsonParseException) {
                throw e;
            }
            throw new JsonParseException("Parse error: " + e.getMessage(), 0, e);
        }
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param buffer the Buffer containing a JSON array
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public JsonArrayCursor streamArray(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        return streamArray(new BufferCursor(buffer));
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
    public JsonArrayCursor streamArray(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return streamArray(new ByteArrayCursor(data, offset, length));
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param json the JSON string containing an array
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public JsonArrayCursor streamArray(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        return streamArray(new StringCursor(json));
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param cursor the input cursor
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public JsonArrayCursor streamArray(InputCursor cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }
        
        if (cursor.isEmpty()) {
            throw new JsonParseException("Empty JSON input", 0);
        }
        
        return streamArrayWithStack(cursor);
    }
    
    /**
     * Stream array using stack-based tokenizer.
     */
    private JsonArrayCursor streamArrayWithStack(InputCursor cursor) {
        try {
            // Reuse tokenizer from ThreadLocal pool
            StackTokenizer tokenizer = TOKENIZER_POOL.get();
            AstStore astStore = tokenizer.tokenize(cursor);
            
            int rootIndex = astStore.getRoot();
            byte rootType = astStore.getType(rootIndex);
            
            if (rootType != AstStore.TYPE_ARRAY) {
                throw new JsonParseException("Expected JSON array, got: " + AstStore.toJsonType(rootType), 0);
            }
            
            JsonArray array = new JsonArray(astStore, rootIndex, cursor);
            return new JsonArrayCursor(cursor, array);
        } catch (Exception e) {
            if (e instanceof JsonParseException) {
                throw e;
            }
            throw new JsonParseException("Parse error: " + e.getMessage(), 0, e);
        }
    }
}