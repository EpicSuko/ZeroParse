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
    
    // Fixed-size array for small cases (most JSON has < 16 views)
    private static final int FIXED_CAPACITY = 16;
    
    private final JsonParser parser;
    
    // Fixed array for fast path (no ArrayList allocation)
    private final JsonValue[] fixedViews;
    private int fixedCount;
    
    // Overflow list for large cases (lazy allocated)
    private List<JsonValue> overflowViews;
    
    // Single-view fast path flag
    private boolean singleViewMode;
    private JsonValue singleView;
    
    /**
     * Get a pooled context from ThreadLocal storage.
     * This is the recommended way to obtain a context (zero allocation).
     * 
     * @return a reusable JsonParseContext
     */
    public static JsonParseContext get() {
        JsonParseContext ctx = CONTEXT_POOL.get();
        ctx.reset();
        return ctx;
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
        this.fixedViews = new JsonValue[FIXED_CAPACITY];
        this.fixedCount = 0;
        this.overflowViews = null;
        this.singleViewMode = false;
        this.singleView = null;
    }
    
    /**
     * Reset this context for reuse (called by ThreadLocal pool).
     */
    private void reset() {
        this.fixedCount = 0;
        this.singleViewMode = false;
        this.singleView = null;
        // Note: Don't clear fixedViews array - we'll overwrite slots
        // Note: Don't null overflowViews - keep capacity for reuse
        if (overflowViews != null) {
            overflowViews.clear();
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
     */
    JsonValue borrowView(byte type, com.suko.zeroparse.stack.AstStore ast, 
                         int idx, InputCursor cursor) {
        JsonValue view;
        
        switch (type) {
            case com.suko.zeroparse.stack.AstStore.TYPE_OBJECT:
                JsonObject obj = ViewPools.borrowObject();
                obj.reset(ast, idx, cursor);
                view = obj;
                break;
                
            case com.suko.zeroparse.stack.AstStore.TYPE_ARRAY:
                JsonArray arr = ViewPools.borrowArray();
                arr.reset(ast, idx, cursor);
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
     */
    private void trackView(JsonValue view) {
        if (view != null && !(view instanceof JsonBoolean) && !(view instanceof JsonNull)) {
            addView(view);
        }
    }
    
    /**
     * Add a view to tracking (optimized with fast paths).
     */
    private void addView(JsonValue view) {
        // Single-view fast path (most common for simple JSON)
        if (fixedCount == 0 && overflowViews == null) {
            singleViewMode = true;
            singleView = view;
            fixedCount = 1;  // Mark as used
            return;
        }
        
        // Exit single-view mode if we get a second view
        if (singleViewMode) {
            singleViewMode = false;
            fixedViews[0] = singleView;
            fixedViews[1] = view;
            fixedCount = 2;
            return;
        }
        
        // Fixed array fast path
        if (fixedCount < FIXED_CAPACITY) {
            fixedViews[fixedCount++] = view;
            return;
        }
        
        // Overflow to ArrayList (rare for most JSON)
        if (overflowViews == null) {
            overflowViews = new ArrayList<>(32);
        }
        overflowViews.add(view);
    }
    
    /**
     * Return all borrowed views back to the pool.
     * Called automatically by try-with-resources.
     */
    @Override
    public void close() {
        // Single-view fast path
        if (singleViewMode) {
            ViewPools.returnView(singleView);
            singleView = null;
            singleViewMode = false;
            fixedCount = 0;
            return;
        }
        
        // Return views from fixed array
        for (int i = 0; i < fixedCount; i++) {
            ViewPools.returnView(fixedViews[i]);
            fixedViews[i] = null;  // Help GC
        }
        fixedCount = 0;
        
        // Return views from overflow list
        if (overflowViews != null && !overflowViews.isEmpty()) {
            for (JsonValue view : overflowViews) {
                ViewPools.returnView(view);
            }
            overflowViews.clear();
        }
    }
    
    /**
     * Get the number of views currently tracked by this context.
     * Useful for debugging and monitoring.
     * 
     * @return the number of tracked views
     */
    public int getTrackedViewCount() {
        if (singleViewMode) {
            return 1;
        }
        int count = fixedCount;
        if (overflowViews != null) {
            count += overflowViews.size();
        }
        return count;
    }
}


