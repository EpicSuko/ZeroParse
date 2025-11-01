package com.suko.zeroparse;

import com.suko.pool.StripedObjectPool;
import com.suko.pool.AutoGrowConfig;

/**
 * Manages object pools for JSON view objects to achieve garbage-free parsing.
 * 
 * <p>This class uses FastPool to reuse view objects across parse operations,
 * dramatically reducing GC pressure and allocation rates.</p>
 */
public final class ViewPools {
    
    // Pool sizes configured for high-throughput crypto trading scenarios
    private static final int OBJECT_STRIPES = 4;
    private static final int OBJECT_STRIPE_SIZE = 64;
    
    private static final int ARRAY_STRIPES = 2;
    private static final int ARRAY_STRIPE_SIZE = 32;
    
    private static final int STRING_STRIPES = 4;
    private static final int STRING_STRIPE_SIZE = 128;
    
    private static final int NUMBER_STRIPES = 4;
    private static final int NUMBER_STRIPE_SIZE = 128;
    
    // Striped pools for each view type
    private static final StripedObjectPool<JsonObject> OBJECT_POOL;
    private static final StripedObjectPool<JsonArray> ARRAY_POOL;
    private static final StripedObjectPool<JsonStringView> STRING_POOL;
    private static final StripedObjectPool<JsonNumberView> NUMBER_POOL;
    
    static {
        // Initialize striped pools with auto-grow enabled (instant growth, cooldown=0)
        OBJECT_POOL = new StripedObjectPool<>(
            JsonObject::new,
            JsonObject::reset,
            OBJECT_STRIPES,
            OBJECT_STRIPE_SIZE
        );
        
        ARRAY_POOL = new StripedObjectPool<>(
            JsonArray::new,
            JsonArray::reset,
            ARRAY_STRIPES,
            ARRAY_STRIPE_SIZE
        );
        
        STRING_POOL = new StripedObjectPool<>(
            JsonStringView::new,
            JsonStringView::reset,
            STRING_STRIPES,
            STRING_STRIPE_SIZE
        );
        
        NUMBER_POOL = new StripedObjectPool<>(
            JsonNumberView::new,
            JsonNumberView::reset,
            NUMBER_STRIPES,
            NUMBER_STRIPE_SIZE
        );
        
        // Enable auto-grow with zero cooldown for instant capacity expansion
        AutoGrowConfig config = new AutoGrowConfig(1, 0, 1, 0);
        OBJECT_POOL.enableAutoGrow(config);
        ARRAY_POOL.enableAutoGrow(config);
        STRING_POOL.enableAutoGrow(config);
        NUMBER_POOL.enableAutoGrow(config);
    }
    
    private ViewPools() {
        // Prevent instantiation
    }
    
    /**
     * Borrow a JsonObject from the pool.
     */
    public static JsonObject borrowObject() {
        return OBJECT_POOL.acquire();
    }
    
    /**
     * Borrow a JsonArray from the pool.
     */
    public static JsonArray borrowArray() {
        return ARRAY_POOL.acquire();
    }
    
    /**
     * Borrow a JsonStringView from the pool.
     */
    public static JsonStringView borrowString() {
        return STRING_POOL.acquire();
    }
    
    /**
     * Borrow a JsonNumberView from the pool.
     */
    public static JsonNumberView borrowNumber() {
        return NUMBER_POOL.acquire();
    }
    
    /**
     * Return a view object back to the pool.
     * Automatically routes to the correct pool based on type.
     */
    public static void returnView(JsonValue view) {
        if (view == null) {
            return;
        }
        
        if (view instanceof JsonObject) {
            OBJECT_POOL.release((JsonObject) view);
        } else if (view instanceof JsonArray) {
            ARRAY_POOL.release((JsonArray) view);
        } else if (view instanceof JsonStringView) {
            STRING_POOL.release((JsonStringView) view);
        } else if (view instanceof JsonNumberView) {
            NUMBER_POOL.release((JsonNumberView) view);
        }
        // Singletons (JsonBoolean, JsonNull) don't need to be pooled
    }
    
    /**
     * Return all views recursively (including nested objects/arrays).
     * Use this for manual cleanup if not using JsonParseContext.
     */
    public static void returnAll(JsonValue view) {
        if (view == null) {
            return;
        }
        
        if (view instanceof JsonObject) {
            JsonObject obj = (JsonObject) view;
            // Return all field values first
            for (java.util.Map.Entry<Utf8Slice, JsonValue> entry : obj) {
                returnAll(entry.getValue());
            }
            OBJECT_POOL.release(obj);
        } else if (view instanceof JsonArray) {
            JsonArray arr = (JsonArray) view;
            // Return all element values first
            for (JsonValue element : arr) {
                returnAll(element);
            }
            ARRAY_POOL.release(arr);
        } else if (view instanceof JsonStringView) {
            STRING_POOL.release((JsonStringView) view);
        } else if (view instanceof JsonNumberView) {
            NUMBER_POOL.release((JsonNumberView) view);
        }
    }
}

