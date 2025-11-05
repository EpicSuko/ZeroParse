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
     * Uses a pooled BufferCursor for zero-allocation parsing.
     * 
     * @param buffer the Buffer containing UTF-8 encoded JSON
     * @return the parsed JSON value
     * @throws JsonParseException if the JSON is invalid
     */
    public JsonValue parse(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        // Use pooled cursor to avoid buffer.getBytes() allocation
        BufferCursor cursor = CursorPools.borrowBufferCursor(buffer);
        try {
            return parse(cursor);
        } finally {
            CursorPools.returnBufferCursor(cursor);
        }
    }
    
    /**
     * Parse JSON from a byte array.
     * Uses a pooled ByteArrayCursor for zero-allocation parsing.
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
        // Use pooled cursor for reuse
        ByteArrayCursor cursor = CursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            return parse(cursor);
        } finally {
            CursorPools.returnByteArrayCursor(cursor);
        }
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
     * Uses pooled views if JsonParseContext is active, otherwise allocates new objects.
     */
    private JsonValue parseWithStack(InputCursor cursor) {
        try {
            // Reuse tokenizer from ThreadLocal pool
            StackTokenizer tokenizer = TOKENIZER_POOL.get();
            AstStore astStore = tokenizer.tokenize(cursor);
            
            int rootIndex = astStore.getRoot();
            byte rootType = astStore.getType(rootIndex);
            
            // Check if we're in a pooled context (simple flag check)
            JsonParseContext context = JsonParseContext.getActiveContext();
            boolean usePooling = (context != null);
            
            switch (rootType) {
                case AstStore.TYPE_OBJECT:
                    if (usePooling) {
                        JsonObject obj = ViewPools.borrowObject();
                        obj.reset(astStore, rootIndex, cursor, context);
                        return obj;
                    }
                    return new JsonObject(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_ARRAY:
                    if (usePooling) {
                        JsonArray arr = ViewPools.borrowArray();
                        arr.reset(astStore, rootIndex, cursor, context);
                        return arr;
                    }
                    return new JsonArray(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_STRING:
                    if (usePooling) {
                        JsonStringView str = ViewPools.borrowString();
                        str.reset(astStore, rootIndex, cursor);
                        return str;
                    }
                    return new JsonStringView(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_NUMBER:
                    if (usePooling) {
                        JsonNumberView num = ViewPools.borrowNumber();
                        num.reset(astStore, rootIndex, cursor);
                        return num;
                    }
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
     * Uses a pooled BufferCursor for zero-allocation parsing.
     * 
     * @param buffer the Buffer containing a JSON array
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public JsonArrayCursor streamArray(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        // Use pooled cursor to avoid buffer.getBytes() allocation
        BufferCursor cursor = CursorPools.borrowBufferCursor(buffer);
        try {
            return streamArray(cursor);
        } finally {
            CursorPools.returnBufferCursor(cursor);
        }
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * Uses a pooled ByteArrayCursor for zero-allocation parsing.
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
        // Use pooled cursor for reuse
        ByteArrayCursor cursor = CursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            return streamArray(cursor);
        } finally {
            CursorPools.returnByteArrayCursor(cursor);
        }
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
            
            // Check if we're in a pooled context (simple flag check)
            JsonParseContext context = JsonParseContext.getActiveContext();
            JsonArray array;
            
            if (context != null) {
                array = ViewPools.borrowArray();
                array.reset(astStore, rootIndex, cursor, context);
            } else {
                array = new JsonArray(astStore, rootIndex, cursor);
            }
            
            return new JsonArrayCursor(cursor, array);
        } catch (Exception e) {
            if (e instanceof JsonParseException) {
                throw e;
            }
            throw new JsonParseException("Parse error: " + e.getMessage(), 0, e);
        }
    }
}