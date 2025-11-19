package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import com.suko.zeroparse.stack.StackTokenizer;
import com.suko.zeroparse.stack.AstStore;

/**
 * A JSON parser that provides zero-copy parsing capabilities.
 *
 * <p>This parser provides efficient parsing of JSON from various input sources,
 * using lazy evaluation and AST-backed views for minimal allocations.</p>
 */
public final class JsonParser {
    
    // Single tokenizer instance reused by this parser (no ThreadLocal)
    private final StackTokenizer tokenizer = new StackTokenizer();
    private final CursorPools cursorPools;
    
    /**
     * Create a new JsonParser.
     */
    public JsonParser() {
        this(new CursorPools());
    }

    JsonParser(CursorPools cursorPools) {
        this.cursorPools = cursorPools;
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
        BufferCursor cursor = cursorPools.borrowBufferCursor(buffer);
        try {
            return parse(cursor);
        } finally {
            cursorPools.returnBufferCursor(cursor);
        }
    }

    // Package-private helper used by JsonParseContext for pooled parsing
    JsonValue parse(Buffer buffer, JsonParseContext context) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        BufferCursor cursor = cursorPools.borrowBufferCursor(buffer);
        try {
            cursor.setContext(context);
            return parse(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnBufferCursor(cursor);
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
        ByteArrayCursor cursor = cursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            return parse(cursor);
        } finally {
            cursorPools.returnByteArrayCursor(cursor);
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
        StringCursor cursor = cursorPools.borrowStringCursor(json);
        try {
            return parse(cursor);
        } finally {
            cursorPools.returnStringCursor(cursor);
        }
    }

    // Package-private helper used by JsonParseContext for pooled parsing
    JsonValue parse(String json, JsonParseContext context) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        StringCursor cursor = cursorPools.borrowStringCursor(json);
        try {
            cursor.setContext(context);
            return parse(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnStringCursor(cursor);
        }
    }

    // Package-private helper used by JsonParseContext for pooled parsing
    JsonValue parse(byte[] data, int offset, int length, JsonParseContext context) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        ByteArrayCursor cursor = cursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            cursor.setContext(context);
            return parse(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnByteArrayCursor(cursor);
        }
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
        
        return parseWithStack(cursor, null);
    }
    
    /**
     * Parse using stack-based tokenizer.
     * Uses pooled views if JsonParseContext is active, otherwise allocates new objects.
     */
    JsonValue parse(InputCursor cursor, JsonParseContext context) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }
        if (cursor.isEmpty()) {
            throw new JsonParseException("Empty JSON input", 0);
        }
        return parseWithStack(cursor, context);
    }
    
    /**
     * Parse using stack-based tokenizer.
     * Uses pooled views when a JsonParseContext is provided, otherwise allocates new objects.
     */
    private JsonValue parseWithStack(InputCursor cursor, JsonParseContext context) {
        try {
            AstStore astStore = tokenizer.tokenize(cursor);
            
            int rootIndex = astStore.getRoot();
            byte rootType = astStore.getType(rootIndex);
            
            boolean usePooling = (context != null);
            
            switch (rootType) {
                case AstStore.TYPE_OBJECT:
                    if (usePooling) {
                        return context.borrowView(rootType, astStore, rootIndex, cursor);
                    }
                    return new JsonObject(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_ARRAY:
                    if (usePooling) {
                        return context.borrowView(rootType, astStore, rootIndex, cursor);
                    }
                    return new JsonArray(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_STRING:
                    if (usePooling) {
                        return context.borrowView(rootType, astStore, rootIndex, cursor);
                    }
                    return new JsonStringView(astStore, rootIndex, cursor);
                    
                case AstStore.TYPE_NUMBER:
                    if (usePooling) {
                        return context.borrowView(rootType, astStore, rootIndex, cursor);
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
        BufferCursor cursor = cursorPools.borrowBufferCursor(buffer);
        try {
            return streamArray(cursor, null);
        } finally {
            cursorPools.returnBufferCursor(cursor);
        }
    }

    // Package-private helper used by JsonParseContext for pooled streaming
    JsonArrayCursor streamArray(Buffer buffer, JsonParseContext context) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        BufferCursor cursor = cursorPools.borrowBufferCursor(buffer);
        try {
            cursor.setContext(context);
            return streamArray(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnBufferCursor(cursor);
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
        ByteArrayCursor cursor = cursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            return streamArray(cursor, null);
        } finally {
            cursorPools.returnByteArrayCursor(cursor);
        }
    }

    // Package-private helper used by JsonParseContext for pooled streaming
    JsonArrayCursor streamArray(byte[] data, int offset, int length, JsonParseContext context) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        ByteArrayCursor cursor = cursorPools.borrowByteArrayCursor(data, offset, length);
        try {
            cursor.setContext(context);
            return streamArray(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnByteArrayCursor(cursor);
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
        StringCursor cursor = cursorPools.borrowStringCursor(json);
        try {
            return streamArray(cursor, null);
        } finally {
            cursorPools.returnStringCursor(cursor);
        }
    }
    
    // Package-private helper used by JsonParseContext for pooled streaming
    JsonArrayCursor streamArray(String json, JsonParseContext context) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        StringCursor cursor = cursorPools.borrowStringCursor(json);
        try {
            cursor.setContext(context);
            return streamArray(cursor, context);
        } finally {
            cursor.setContext(null);
            cursorPools.returnStringCursor(cursor);
        }
    }
    
    /**
     * Create a streaming array cursor for element-by-element extraction.
     * 
     * @param cursor the input cursor
     * @return a cursor for streaming array elements
     * @throws JsonParseException if the JSON is invalid or not an array
     */
    public JsonArrayCursor streamArray(InputCursor cursor) {
        return streamArray(cursor, null);
    }
    
    JsonArrayCursor streamArray(InputCursor cursor, JsonParseContext context) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }
        
        if (cursor.isEmpty()) {
            throw new JsonParseException("Empty JSON input", 0);
        }
        
        return streamArrayWithStack(cursor, context);
    }
    
    /**
     * Stream array using stack-based tokenizer.
     */
    private JsonArrayCursor streamArrayWithStack(InputCursor cursor, JsonParseContext context) {
        try {
            AstStore astStore = tokenizer.tokenize(cursor);
            
            int rootIndex = astStore.getRoot();
            byte rootType = astStore.getType(rootIndex);
            
            if (rootType != AstStore.TYPE_ARRAY) {
                throw new JsonParseException("Expected JSON array, got: " + AstStore.toJsonType(rootType), 0);
            }
            
            JsonArray array;
            if (context != null) {
                JsonValue view = context.borrowView(AstStore.TYPE_ARRAY, astStore, rootIndex, cursor);
                array = view.asArray();
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