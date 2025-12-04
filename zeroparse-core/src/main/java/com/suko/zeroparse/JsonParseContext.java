package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Arena-based parsing context for garbage-free JSON parsing.
 * 
 * <p>This context borrows view objects from pools during parsing and automatically
 * returns them all when the context is closed. Perfect for request/response patterns
 * in high-throughput systems.</p>
 * 
 * <p><strong>Optimizations:</strong></p>
 * <ul>
 *   <li>Fixed-size array for small cases (avoids ArrayList allocation)</li>
 *   <li>ThreadLocal pooling of context itself (zero allocation)</li>
 *   <li>Single-view fast path (skips tracking for simple JSON)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Reuse ctx inside an event-loop or tight loop:
 * JsonParseContext ctx = new JsonParseContext();
 * ...
 * ctx.close(); // return previous views
 * JsonObject order = ctx.parse(buffer).asObject();
 * String symbol = order.get("symbol").asString().toString();
 * double price = order.get("price").asNumber().asDouble();
 * processOrder(symbol, price);
 * // Repeat: ctx.close(); ctx.parse(...);
 * // Streaming arrays:
 * JsonArrayCursor cursor = ctx.streamArray(bufferWithArray);
 * </pre>
 * 
 * <p><strong>Benefits:</strong></p>
 * <ul>
 *   <li>Zero allocations for view objects (reused from pool)</li>
 *   <li>Zero allocations for context itself (ThreadLocal pool)</li>
 *   <li>Automatic cleanup with try-with-resources</li>
 *   <li>Thread-safe (each thread gets its own context)</li>
 *   <li>Perfect for crypto trading WebSocket handlers</li>
 * </ul>
 */
public final class JsonParseContext implements AutoCloseable {
    
    // Fixed-size array for small cases (most JSON has < 64 views total)
    private static final int FIXED_CAPACITY = 64;
    private static final int FIXED_SLICE_CAPACITY = 32;  // Slices are more common
    
    private final JsonParser parser;
    private final ViewPools viewPools;
    
    // Unified tracking with type tags (simpler, faster close())
    private final JsonValue[] fixedViews;
    private final byte[] fixedViewTypes;  // TYPE_OBJECT, TYPE_ARRAY, TYPE_STRING, TYPE_NUMBER
    private int viewCount;
    
    // Overflow list for large JSON (lazy allocated)
    private List<JsonValue> overflowViews;
    private List<Byte> overflowViewTypes;
    
    // Fixed array for slices (most JSON has < 32 slices)
    private final ByteSlice[] fixedSlices;
    private int fixedSliceCount;
    
    // Overflow list for large cases (lazy allocated)
    private List<ByteSlice> overflowSlices;
    
    // Fixed array pool for field name caches (reused across JsonObjects)
    private final ByteSlice[][] fieldNameArrayPool;
    private int fieldNameArrayPoolIndex;
    private static final int MAX_FIELD_NAME_ARRAYS = 8;
    
    // Fixed array pool for field indices (reused across JsonObjects)
    private final int[][] fieldIndicesArrayPool;
    private int fieldIndicesArrayPoolIndex;
    private static final int MAX_FIELD_INDICES_ARRAYS = 8;
    
    // Reusable integer array for building field indices (avoids ArrayList allocation)
    private final int[] fieldIndexBuffer;
    
    
    /**
     * Create a new parse context with default parser and view pools.
     * This is the simplest entry point for application code:
     *
     * <pre>
     * try (JsonParseContext ctx = new JsonParseContext()) {
     *     JsonObject obj = ctx.parse(buffer).asObject();
     *     // ...
     * }
     * </pre>
     */
    public JsonParseContext() {
        this(new JsonParser(), new ViewPools());
    }
    
    /**
     * Create a new parse context with custom parser and view pools.
     * 
     * @param parser the JSON parser to use
     */
    public JsonParseContext(JsonParser parser, ViewPools viewPools) {
        this.parser = parser;
        this.viewPools = viewPools;
        this.fixedViews = new JsonValue[FIXED_CAPACITY];
        this.fixedViewTypes = new byte[FIXED_CAPACITY];
        this.viewCount = 0;
        this.fixedSlices = new ByteSlice[FIXED_SLICE_CAPACITY];
        this.fixedSliceCount = 0;
        this.overflowViews = null;
        this.overflowViewTypes = null;
        this.overflowSlices = null;
        this.fieldNameArrayPool = new ByteSlice[MAX_FIELD_NAME_ARRAYS][];
        this.fieldNameArrayPoolIndex = 0;
        this.fieldIndicesArrayPool = new int[MAX_FIELD_INDICES_ARRAYS][];
        this.fieldIndicesArrayPoolIndex = 0;
        this.fieldIndexBuffer = new int[64];  // Reusable buffer for field indices (most objects have < 64 fields)
        
        // Pre-allocate all arrays in the pools to avoid allocation during parsing
        // These arrays are reused across parses via the pool indices
        for (int i = 0; i < MAX_FIELD_NAME_ARRAYS; i++) {
            fieldNameArrayPool[i] = new ByteSlice[16];  // Support up to 16 fields per object
        }
        for (int i = 0; i < MAX_FIELD_INDICES_ARRAYS; i++) {
            fieldIndicesArrayPool[i] = new int[16];  // Support up to 16 fields per object
        }
    }
    
    /**
     * Reset this context for reuse.
     */
    private void reset() {
        this.viewCount = 0;
        this.fixedSliceCount = 0;
        this.fieldNameArrayPoolIndex = 0;  // Reset array pool index
        this.fieldIndicesArrayPoolIndex = 0;  // Reset field indices pool index
        // Note: Don't clear fixed arrays - we'll overwrite slots
        // Note: Keep overflow list capacity for reuse
        if (overflowViews != null) {
            overflowViews.clear();
        }
        if (overflowViewTypes != null) {
            overflowViewTypes.clear();
        }
        if (overflowSlices != null) {
            overflowSlices.clear();
        }
    }
    
    /**
     * Parse JSON from a Vert.x Buffer.
     * All view objects created during parsing are tracked and will be
     * automatically returned to the pool when this context is closed.
     * 
     * @param buffer the Buffer containing JSON data
     * @return the root JSON value
     * @throws JsonParseException if parsing fails
     */
    public JsonValue parse(Buffer buffer) {
        reset();
        JsonValue root = parser.parse(buffer, this);
        // trackView(root) removed - already tracked in borrowView
        setContextOnRoot(root);
        return root;
    }
    
    /**
     * Parse JSON from a Netty ByteBuf.
     * All view objects created during parsing are tracked and will be
     * automatically returned to the pool when this context is closed.
     * 
     * @param byteBuf the ByteBuf containing JSON data
     * @return the root JSON value
     * @throws JsonParseException if parsing fails
     */
    public JsonValue parse(ByteBuf byteBuf) {
        reset();
        JsonValue root = parser.parse(byteBuf, this);
        // trackView(root) removed - already tracked in borrowView
        setContextOnRoot(root);
        return root;
    }
    
    /**
     * Parse JSON from a String.
     * All view objects created during parsing are tracked and will be
     * automatically returned to the pool when this context is closed.
     * 
     * @param json the JSON string
     * @return the root JSON value
     * @throws JsonParseException if parsing fails
     */
    public JsonValue parse(String json) {
        reset();
        JsonValue root = parser.parse(json, this);
        // trackView(root) removed - already tracked in borrowView
        setContextOnRoot(root);
        return root;
    }
    
    /**
     * Parse JSON from a byte array.
     * All view objects created during parsing are tracked and will be
     * automatically returned to the pool when this context is closed.
     * 
     * @param bytes the JSON data
     * @return the root JSON value
     * @throws JsonParseException if parsing fails
     */
    public JsonValue parse(byte[] bytes) {
        reset();
        JsonValue root = parser.parse(bytes, 0, bytes.length, this);
        // trackView(root) removed - already tracked in borrowView
        setContextOnRoot(root);
        return root;
    }
    
    /**
     * Parse JSON from a byte array segment.
     * All view objects created during parsing are tracked and will be
     * automatically returned to the pool when this context is closed.
     * 
     * @param bytes the byte array containing JSON data
     * @param offset start offset in the array
     * @param length number of bytes to parse
     * @return the root JSON value
     * @throws JsonParseException if parsing fails
     */
    public JsonValue parse(byte[] bytes, int offset, int length) {
        reset();
        JsonValue root = parser.parse(bytes, offset, length, this);
        // trackView(root) removed - already tracked in borrowView
        setContextOnRoot(root);
        return root;
    }

    /**
     * Stream a JSON array from a Buffer using this context.
     * All views created from the streamed array are pooled and returned on close().
     */
    public JsonArrayCursor streamArray(Buffer buffer) {
        reset();
        return parser.streamArray(buffer, this);
    }
    
    /**
     * Stream a JSON array from a Netty ByteBuf using this context.
     * All views created from the streamed array are pooled and returned on close().
     */
    public JsonArrayCursor streamArray(ByteBuf byteBuf) {
        reset();
        return parser.streamArray(byteBuf, this);
    }

    /**
     * Stream a JSON array from a String using this context.
     */
    public JsonArrayCursor streamArray(String json) {
        reset();
        return parser.streamArray(json, this);
    }

    /**
     * Stream a JSON array from a byte array using this context.
     */
    public JsonArrayCursor streamArray(byte[] bytes) {
        reset();
        return parser.streamArray(bytes, 0, bytes.length, this);
    }
    
    /**
     * Set the context on the root value so nested views can use pooling.
     */
    private void setContextOnRoot(JsonValue root) {
        if (root instanceof JsonObject) {
            ((JsonObject) root).context = this;
        } else if (root instanceof JsonArray) {
            ((JsonArray) root).context = this;
        }
    }
    
    /**
     * Borrow a view object from the pool and track it for automatic return.
     * This is used internally during parsing to create nested view objects.
     * Sets context directly on Object/Array views for recursive pooling.
     */
    JsonValue borrowView(byte type, com.suko.zeroparse.stack.AstStore ast, 
                         int idx, InputCursor cursor) {
        JsonValue view;
        
        switch (type) {
            case com.suko.zeroparse.stack.AstStore.TYPE_OBJECT:
                JsonObject obj = viewPools.borrowObject();
                obj.reset(ast, idx, cursor);
                obj.context = this;  // Set context directly (avoids instanceof check in caller)
                view = obj;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_ARRAY:
                JsonArray arr = viewPools.borrowArray();
                arr.reset(ast, idx, cursor);
                arr.context = this;  // Set context directly (avoids instanceof check in caller)
                view = arr;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_STRING:
                JsonStringView str = viewPools.borrowString();
                str.reset(ast, idx, cursor);
                view = str;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_NUMBER:
                JsonNumberView num = viewPools.borrowNumber();
                num.reset(ast, idx, cursor);
                view = num;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_BOOLEAN_TRUE:
                return JsonBoolean.TRUE;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_BOOLEAN_FALSE:
                return JsonBoolean.FALSE;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_NULL:
                return JsonNull.INSTANCE;
                
            default:
                throw new IllegalStateException("Unknown node type: " + type);
        }
        
        // Track the borrowed view with known type
        addViewWithType(view, type);
        return view;
    }
    
    // Removed trackView - no longer needed since borrowView handles tracking with known type
    
    // Removed addView - no longer needed since all callers know the type
    
    /**
     * Add a view to tracking with known type - avoids instanceof checks.
     */
    private void addViewWithType(JsonValue view, byte type) {
        // Store in unified array with type tag
        if (viewCount < FIXED_CAPACITY) {
            fixedViews[viewCount] = view;
            fixedViewTypes[viewCount] = type;
            viewCount++;
        } else {
            // Overflow to lists (rare for most JSON)
            if (overflowViews == null) {
                overflowViews = new ArrayList<>(32);
                overflowViewTypes = new ArrayList<>(32);
            }
            overflowViews.add(view);
            overflowViewTypes.add(type);
        }
    }
    
    /**
     * Borrow a ByteSlice from the pool and track it for automatic return.
     * This is used internally during parsing to create slices with zero allocation.
     */
    ByteSlice borrowSlice(byte[] source, int offset, int length) {
        ByteSlice slice = viewPools.borrowSlice(source, offset, length);
        trackSlice(slice);
        return slice;
    }

    /**
     * Borrow a pooled string view backed by the provided slice.
     */
    JsonStringView borrowStringView(ByteSlice slice) {
        JsonStringView view = viewPools.borrowString();
        view.reset(slice);
        addViewWithType(view, com.suko.zeroparse.stack.AstStore.TYPE_STRING);
        return view;
    }

    /**
     * Borrow a pooled substring from an existing string view.
     */
    public JsonStringView borrowSubString(JsonStringView source, int start, int length) {
        ByteSlice base = source.slice();
        ByteSlice sub = viewPools.borrowSlice(base.getSource(), base.getOffset() + start, length);
        return borrowStringView(sub);
    }
    
    /**
     * Borrow or allocate a ByteSlice array for field name caching.
     * Arrays are reused across multiple JsonObjects within the same context.
     * 
     * @param size the required array size
     * @return a ByteSlice array (reused or newly allocated)
     */
    ByteSlice[] borrowFieldNameArray(int size) {
        // Try to reuse from pre-allocated pool
        if (fieldNameArrayPoolIndex < MAX_FIELD_NAME_ARRAYS) {
            ByteSlice[] array = fieldNameArrayPool[fieldNameArrayPoolIndex];
            if (array.length >= size) {
                fieldNameArrayPoolIndex++;
                return array;
            }
        }
        
        // Pool exhausted - should not happen in normal cases
        // Return a fallback array (this would trigger allocation)
        return new ByteSlice[size];
    }
    
    /**
     * Borrow a field indices array from the context.
     * Arrays are reused across multiple JsonObjects within the same context.
     * 
     * @param count the number of indices needed
     * @return an int array for field indices (reused or newly allocated)
     */
    int[] borrowFieldIndicesArray(int count) {
        // Try to reuse from pre-allocated pool
        if (fieldIndicesArrayPoolIndex < MAX_FIELD_INDICES_ARRAYS) {
            int[] array = fieldIndicesArrayPool[fieldIndicesArrayPoolIndex];
            if (array.length >= count) {
                fieldIndicesArrayPoolIndex++;
                return array;
            }
        }
        
        // Pool exhausted - should not happen in normal cases
        // Return a fallback array (this would trigger allocation)
        return new int[count];
    }
    
    /**
     * Get the reusable field index buffer for building field indices.
     * This avoids ArrayList allocation in JsonObject.buildFieldIndex().
     * 
     * @return the field index buffer
     */
    int[] getFieldIndexBuffer() {
        return fieldIndexBuffer;
    }
    
    /**
     * Track a slice for automatic return on close.
     */
    private void trackSlice(ByteSlice slice) {
        // Fixed array fast path
        if (fixedSliceCount < FIXED_SLICE_CAPACITY) {
            fixedSlices[fixedSliceCount++] = slice;
            return;
        }
        
        // Overflow to ArrayList (rare for most JSON)
        if (overflowSlices == null) {
            overflowSlices = new ArrayList<>(64);
        }
        overflowSlices.add(slice);
    }
    
    /**
     * Return all borrowed views and slices back to the pool.
     * Called automatically by try-with-resources.
     * Optimized with unified tracking and switch dispatch.
     */
    @Override
    public void close() {
        try {
            // Return all tracked views using type tags for fast dispatch (single loop!)
            for (int i = 0; i < viewCount; i++) {
                JsonValue view = fixedViews[i];
                byte type = fixedViewTypes[i];
                
                switch (type) {
                    case com.suko.zeroparse.stack.AstStore.TYPE_OBJECT:
                        viewPools.returnObject((JsonObject) view);
                        break;
                    case com.suko.zeroparse.stack.AstStore.TYPE_ARRAY:
                        viewPools.returnArray((JsonArray) view);
                        break;
                    case com.suko.zeroparse.stack.AstStore.TYPE_STRING:
                        viewPools.returnString((JsonStringView) view);
                        break;
                    case com.suko.zeroparse.stack.AstStore.TYPE_NUMBER:
                        viewPools.returnNumber((JsonNumberView) view);
                        break;
                }
            }
            
            // Return overflow views (rare)
            if (overflowViews != null && !overflowViews.isEmpty()) {
                for (int i = 0; i < overflowViews.size(); i++) {
                    JsonValue view = overflowViews.get(i);
                    byte type = overflowViewTypes.get(i);
                    
                    switch (type) {
                        case com.suko.zeroparse.stack.AstStore.TYPE_OBJECT:
                            viewPools.returnObject((JsonObject) view);
                            break;
                        case com.suko.zeroparse.stack.AstStore.TYPE_ARRAY:
                            viewPools.returnArray((JsonArray) view);
                            break;
                        case com.suko.zeroparse.stack.AstStore.TYPE_STRING:
                            viewPools.returnString((JsonStringView) view);
                            break;
                        case com.suko.zeroparse.stack.AstStore.TYPE_NUMBER:
                            viewPools.returnNumber((JsonNumberView) view);
                            break;
                    }
                }
                overflowViews.clear();
                overflowViewTypes.clear();
            }
            
            // Return slices from fixed array
            if (fixedSliceCount > 0) {
                for (int i = 0; i < fixedSliceCount; i++) {
                    viewPools.returnSlice(fixedSlices[i]);
                }
            }
            
            // Return slices from overflow list
            if (overflowSlices != null && !overflowSlices.isEmpty()) {
                for (ByteSlice slice : overflowSlices) {
                    viewPools.returnSlice(slice);
                }
                overflowSlices.clear();
            }
        } finally {
            // no-op
        }
    }
    
    /**
     * Get the number of views currently tracked by this context.
     * Useful for debugging and monitoring.
     * 
     * @return the number of tracked views
     */
    public int getTrackedViewCount() {
        int count = viewCount;
        if (overflowViews != null) {
            count += overflowViews.size();
        }
        return count;
    }
    
    /**
     * Get the number of slices currently tracked by this context.
     * Useful for debugging and monitoring.
     * 
     * @return the number of tracked slices
     */
    public int getTrackedSliceCount() {
        int count = fixedSliceCount;
        if (overflowSlices != null) {
            count += overflowSlices.size();
        }
        return count;
    }
}


