package com.suko.zeroparse;

import com.suko.pool.ArrayObjectPool;
import com.suko.pool.ObjectPool;

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
    
    private static final int SLICE_STRIPES = 4;
    private static final int SLICE_STRIPE_SIZE = 256;  // Slices are very common
    
    // Striped pools for each view type (public for direct access from JsonParseContext)
    public static final ObjectPool<JsonObject> OBJECT_POOL;
    public static final ObjectPool<JsonArray> ARRAY_POOL;
    public static final ObjectPool<JsonStringView> STRING_POOL;
    public static final ObjectPool<JsonNumberView> NUMBER_POOL;
    public static final ObjectPool<Utf8Slice> SLICE_POOL;
    
    static {

        OBJECT_POOL = new ArrayObjectPool<>(OBJECT_STRIPES * OBJECT_STRIPE_SIZE, JsonObject.class);
        ARRAY_POOL = new ArrayObjectPool<>(ARRAY_STRIPES * ARRAY_STRIPE_SIZE, JsonArray.class);
        STRING_POOL = new ArrayObjectPool<>(STRING_STRIPES * STRING_STRIPE_SIZE, JsonStringView.class);
        NUMBER_POOL = new ArrayObjectPool<>(NUMBER_STRIPES * NUMBER_STRIPE_SIZE, JsonNumberView.class);
        SLICE_POOL = new ArrayObjectPool<>(SLICE_STRIPES * SLICE_STRIPE_SIZE, Utf8Slice.class);
    }
    
    private ViewPools() {
        // Prevent instantiation
    }
    
    /**
     * Borrow a JsonObject from the pool.
     */
    public static JsonObject borrowObject() {
        return OBJECT_POOL.get();
    }
    
    /**
     * Borrow a JsonArray from the pool.
     */
    public static JsonArray borrowArray() {
        return ARRAY_POOL.get();
    }
    
    /**
     * Borrow a JsonStringView from the pool.
     */
    public static JsonStringView borrowString() {
        return STRING_POOL.get();
    }
    
    /**
     * Borrow a JsonNumberView from the pool.
     */
    public static JsonNumberView borrowNumber() {
        return NUMBER_POOL.get();
    }
    
    /**
     * Borrow a Utf8Slice from the pool.
     * The slice is returned uninitialized - caller must call reset().
     */
    public static Utf8Slice borrowSlice() {
        return SLICE_POOL.get();
    }
    
    /**
     * Borrow a Utf8Slice from the pool and initialize it.
     * 
     * @param source the source byte array
     * @param offset the starting offset
     * @param length the length of the slice
     * @return a pooled and initialized Utf8Slice
     */
    public static Utf8Slice borrowSlice(byte[] source, int offset, int length) {
        Utf8Slice slice = SLICE_POOL.get();
        slice.reset(source, offset, length);
        return slice;
    }
    
    /**
     * Return a Utf8Slice back to the pool.
     */
    public static void returnSlice(Utf8Slice slice) {
        if (slice != null) {
            SLICE_POOL.release(slice);
        }
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

