package com.suko.zeroparse;

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
 * try (JsonParseContext ctx = JsonParseContext.get()) {
 *     JsonObject order = ctx.parse(buffer).asObject();
 *     String symbol = order.get("symbol").asString().toString();
 *     double price = order.get("price").asNumber().asDouble();
 *     processOrder(symbol, price);
 * }  // All borrowed views automatically returned to pool
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
    
    // ThreadLocal pool for context reuse (eliminates context allocation)
    private static final ThreadLocal<JsonParseContext> CONTEXT_POOL = 
        ThreadLocal.withInitial(JsonParseContext::new);
    
    // Fixed-size array for small cases (most JSON has < 16 views per type)
    private static final int FIXED_CAPACITY = 16;
    private static final int FIXED_SLICE_CAPACITY = 32;  // Slices are more common
    
    private final JsonParser parser;
    
    // Type-specific fixed arrays (avoids instanceof checks on return)
    private final JsonObject[] fixedObjects;
    private final JsonArray[] fixedArrays;
    private final JsonStringView[] fixedStrings;
    private final JsonNumberView[] fixedNumbers;
    private int objectCount;
    private int arrayCount;
    private int stringCount;
    private int numberCount;
    
    // Type-specific overflow lists (lazy allocated)
    private List<JsonObject> overflowObjects;
    private List<JsonArray> overflowArrays;
    private List<JsonStringView> overflowStrings;
    private List<JsonNumberView> overflowNumbers;
    
    // Fixed array for slices (most JSON has < 32 slices)
    private final Utf8Slice[] fixedSlices;
    private int fixedSliceCount;
    
    // Overflow list for large cases (lazy allocated)
    private List<Utf8Slice> overflowSlices;
    
    // Fixed array pool for field name caches (reused across JsonObjects)
    private final Utf8Slice[][] fieldNameArrayPool;
    private int fieldNameArrayPoolIndex;
    private static final int MAX_FIELD_NAME_ARRAYS = 8;
    
    // Fixed array pool for field indices (reused across JsonObjects)
    private final int[][] fieldIndicesArrayPool;
    private int fieldIndicesArrayPoolIndex;
    private static final int MAX_FIELD_INDICES_ARRAYS = 8;
    
    // Reusable integer array for building field indices (avoids ArrayList allocation)
    private final int[] fieldIndexBuffer;
    
    // Active flag (cheaper than ThreadLocal lookup)
    private boolean active;
    
    /**
     * Get a pooled context from ThreadLocal storage.
     * This is the recommended way to obtain a context (zero allocation).
     * 
     * @return a reusable JsonParseContext
     */
    public static JsonParseContext get() {
        JsonParseContext ctx = CONTEXT_POOL.get();
        ctx.reset();
        ctx.active = true;  // Mark as active
        return ctx;
    }
    
    /**
     * Get the currently active context for this thread, if any.
     * Used internally by JsonParser to detect pooled vs non-pooled parsing.
     * 
     * @return the active context, or null if no context is active
     */
    static JsonParseContext getActiveContext() {
        JsonParseContext ctx = CONTEXT_POOL.get();
        return ctx.active ? ctx : null;
    }
    
    /**
     * Create a new parse context (for manual management).
     * Prefer using {@link #get()} which reuses contexts from ThreadLocal pool.
     */
    public JsonParseContext() {
        this(new JsonParser());
    }
    
    /**
     * Create a new parse context with custom parser configuration.
     * 
     * @param parser the JSON parser to use
     */
    public JsonParseContext(JsonParser parser) {
        this.parser = parser;
        this.fixedObjects = new JsonObject[FIXED_CAPACITY];
        this.fixedArrays = new JsonArray[FIXED_CAPACITY];
        this.fixedStrings = new JsonStringView[FIXED_CAPACITY];
        this.fixedNumbers = new JsonNumberView[FIXED_CAPACITY];
        this.objectCount = 0;
        this.arrayCount = 0;
        this.stringCount = 0;
        this.numberCount = 0;
        this.fixedSlices = new Utf8Slice[FIXED_SLICE_CAPACITY];
        this.fixedSliceCount = 0;
        this.overflowObjects = null;
        this.overflowArrays = null;
        this.overflowStrings = null;
        this.overflowNumbers = null;
        this.overflowSlices = null;
        this.fieldNameArrayPool = new Utf8Slice[MAX_FIELD_NAME_ARRAYS][];
        this.fieldNameArrayPoolIndex = 0;
        this.fieldIndicesArrayPool = new int[MAX_FIELD_INDICES_ARRAYS][];
        this.fieldIndicesArrayPoolIndex = 0;
        this.fieldIndexBuffer = new int[64];  // Reusable buffer for field indices (most objects have < 64 fields)
        
        // Pre-allocate all arrays in the pools to avoid allocation during parsing
        // These arrays are reused across parses via the pool indices
        for (int i = 0; i < MAX_FIELD_NAME_ARRAYS; i++) {
            fieldNameArrayPool[i] = new Utf8Slice[16];  // Support up to 16 fields per object
        }
        for (int i = 0; i < MAX_FIELD_INDICES_ARRAYS; i++) {
            fieldIndicesArrayPool[i] = new int[16];  // Support up to 16 fields per object
        }
    }
    
    /**
     * Reset this context for reuse (called by ThreadLocal pool).
     */
    private void reset() {
        this.objectCount = 0;
        this.arrayCount = 0;
        this.stringCount = 0;
        this.numberCount = 0;
        this.active = false;  // Will be set to true by get()
        this.fixedSliceCount = 0;
        this.fieldNameArrayPoolIndex = 0;  // Reset array pool index
        this.fieldIndicesArrayPoolIndex = 0;  // Reset field indices pool index
        // Note: Don't clear fixed arrays - we'll overwrite slots
        // Note: Keep overflow list capacity for reuse
        if (overflowObjects != null) {
            overflowObjects.clear();
        }
        if (overflowArrays != null) {
            overflowArrays.clear();
        }
        if (overflowStrings != null) {
            overflowStrings.clear();
        }
        if (overflowNumbers != null) {
            overflowNumbers.clear();
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
        JsonValue root = parser.parse(buffer);
        trackView(root);
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
        JsonValue root = parser.parse(json);
        trackView(root);
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
        JsonValue root = parser.parse(bytes, 0, bytes.length);
        trackView(root);
        setContextOnRoot(root);
        return root;
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
                JsonObject obj = ViewPools.borrowObject();
                obj.reset(ast, idx, cursor);
                obj.context = this;  // Set context directly (avoids instanceof check in caller)
                view = obj;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_ARRAY:
                JsonArray arr = ViewPools.borrowArray();
                arr.reset(ast, idx, cursor);
                arr.context = this;  // Set context directly (avoids instanceof check in caller)
                view = arr;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_STRING:
                JsonStringView str = ViewPools.borrowString();
                str.reset(ast, idx, cursor);
                view = str;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_NUMBER:
                JsonNumberView num = ViewPools.borrowNumber();
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
        
        // Track the borrowed view
        addView(view);
        return view;
    }
    
    /**
     * Track a view for automatic return on close.
     * Package-private for use by JsonParser.
     */
    void trackView(JsonValue view) {
        if (view != null && !(view instanceof JsonBoolean) && !(view instanceof JsonNull)) {
            addView(view);
        }
    }
    
    /**
     * Add a view to tracking (optimized with type-specific arrays to avoid instanceof on return).
     */
    private void addView(JsonValue view) {
        // Route to type-specific array (eliminates instanceof checks later)
        if (view instanceof JsonObject) {
            JsonObject obj = (JsonObject) view;
            if (objectCount < FIXED_CAPACITY) {
                fixedObjects[objectCount++] = obj;
            } else {
                if (overflowObjects == null) {
                    overflowObjects = new ArrayList<>(16);
                }
                overflowObjects.add(obj);
            }
        } else if (view instanceof JsonArray) {
            JsonArray arr = (JsonArray) view;
            if (arrayCount < FIXED_CAPACITY) {
                fixedArrays[arrayCount++] = arr;
            } else {
                if (overflowArrays == null) {
                    overflowArrays = new ArrayList<>(16);
                }
                overflowArrays.add(arr);
            }
        } else if (view instanceof JsonStringView) {
            JsonStringView str = (JsonStringView) view;
            if (stringCount < FIXED_CAPACITY) {
                fixedStrings[stringCount++] = str;
            } else {
                if (overflowStrings == null) {
                    overflowStrings = new ArrayList<>(32);
                }
                overflowStrings.add(str);
            }
        } else if (view instanceof JsonNumberView) {
            JsonNumberView num = (JsonNumberView) view;
            if (numberCount < FIXED_CAPACITY) {
                fixedNumbers[numberCount++] = num;
            } else {
                if (overflowNumbers == null) {
                    overflowNumbers = new ArrayList<>(32);
                }
                overflowNumbers.add(num);
            }
        }
    }
    
    /**
     * Borrow a Utf8Slice from the pool and track it for automatic return.
     * This is used internally during parsing to create slices with zero allocation.
     */
    Utf8Slice borrowSlice(byte[] source, int offset, int length) {
        Utf8Slice slice = ViewPools.borrowSlice(source, offset, length);
        trackSlice(slice);
        return slice;
    }
    
    /**
     * Borrow or allocate a Utf8Slice array for field name caching.
     * Arrays are reused across multiple JsonObjects within the same context.
     * 
     * @param size the required array size
     * @return a Utf8Slice array (reused or newly allocated)
     */
    Utf8Slice[] borrowFieldNameArray(int size) {
        // Try to reuse from pre-allocated pool
        if (fieldNameArrayPoolIndex < MAX_FIELD_NAME_ARRAYS) {
            Utf8Slice[] array = fieldNameArrayPool[fieldNameArrayPoolIndex];
            if (array.length >= size) {
                fieldNameArrayPoolIndex++;
                return array;
            }
        }
        
        // Pool exhausted - should not happen in normal cases
        // Return a fallback array (this would trigger allocation)
        return new Utf8Slice[size];
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
    private void trackSlice(Utf8Slice slice) {
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
     * Optimized with early exits for empty arrays.
     */
    @Override
    public void close() {
        try {
            // Return all tracked views directly to their pools (no instanceof checks!)
            // Early exit optimization: skip loops if count is 0
            
            // Objects
            if (objectCount > 0) {
                for (int i = 0; i < objectCount; i++) {
                    ViewPools.OBJECT_POOL.release(fixedObjects[i]);
                }
                if (overflowObjects != null && !overflowObjects.isEmpty()) {
                    for (JsonObject obj : overflowObjects) {
                        ViewPools.OBJECT_POOL.release(obj);
                    }
                    overflowObjects.clear();
                }
            }
            
            // Arrays
            if (arrayCount > 0) {
                for (int i = 0; i < arrayCount; i++) {
                    ViewPools.ARRAY_POOL.release(fixedArrays[i]);
                }
                if (overflowArrays != null && !overflowArrays.isEmpty()) {
                    for (JsonArray arr : overflowArrays) {
                        ViewPools.ARRAY_POOL.release(arr);
                    }
                    overflowArrays.clear();
                }
            }
            
            // Strings
            if (stringCount > 0) {
                for (int i = 0; i < stringCount; i++) {
                    ViewPools.STRING_POOL.release(fixedStrings[i]);
                }
                if (overflowStrings != null && !overflowStrings.isEmpty()) {
                    for (JsonStringView str : overflowStrings) {
                        ViewPools.STRING_POOL.release(str);
                    }
                    overflowStrings.clear();
                }
            }
            
            // Numbers
            if (numberCount > 0) {
                for (int i = 0; i < numberCount; i++) {
                    ViewPools.NUMBER_POOL.release(fixedNumbers[i]);
                }
                if (overflowNumbers != null && !overflowNumbers.isEmpty()) {
                    for (JsonNumberView num : overflowNumbers) {
                        ViewPools.NUMBER_POOL.release(num);
                    }
                    overflowNumbers.clear();
                }
            }
            
            // Return slices from fixed array
            if (fixedSliceCount > 0) {
                for (int i = 0; i < fixedSliceCount; i++) {
                    ViewPools.returnSlice(fixedSlices[i]);
                }
            }
            
            // Return slices from overflow list
            if (overflowSlices != null && !overflowSlices.isEmpty()) {
                for (Utf8Slice slice : overflowSlices) {
                    ViewPools.returnSlice(slice);
                }
                overflowSlices.clear();
            }
        } finally {
            // Clear active flag (zero allocation)
            active = false;
        }
    }
    
    /**
     * Get the number of views currently tracked by this context.
     * Useful for debugging and monitoring.
     * 
     * @return the number of tracked views
     */
    public int getTrackedViewCount() {
        int count = objectCount + arrayCount + stringCount + numberCount;
        if (overflowObjects != null) {
            count += overflowObjects.size();
        }
        if (overflowArrays != null) {
            count += overflowArrays.size();
        }
        if (overflowStrings != null) {
            count += overflowStrings.size();
        }
        if (overflowNumbers != null) {
            count += overflowNumbers.size();
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


