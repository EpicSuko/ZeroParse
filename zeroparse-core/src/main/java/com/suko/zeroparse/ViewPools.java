package com.suko.zeroparse;

import com.suko.pool.ArrayObjectPool;
import com.suko.pool.ObjectPool;

/**
 * Manages object pools for JSON view objects to achieve garbage-free parsing.
 *
 * <p>This class is intentionally <strong>instance-based</strong> so each
 * environment (e.g. Vert.x event-loop, request scope, or benchmark) can have
 * its own pools without any static or ThreadLocal shared state.</p>
 */
public class ViewPools {
    
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
    
    // Pools for each view type (scoped to this ViewPools instance)
    private final ObjectPool<JsonObject> objectPool;
    private final ObjectPool<JsonArray> arrayPool;
    private final ObjectPool<JsonStringView> stringPool;
    private final ObjectPool<JsonNumberView> numberPool;
    private final ObjectPool<ByteSlice> slicePool;
    
    /**
     * Create a new set of view pools.
     * Typically you create one {@code ViewPools} per single-threaded environment
     * (e.g. Vert.x event loop) and share it with {@link JsonParser} /
     * {@link JsonParseContext}.
     */
    public ViewPools() {
        this.objectPool = new ArrayObjectPool<>(OBJECT_STRIPES * OBJECT_STRIPE_SIZE, JsonObject.class);
        this.arrayPool = new ArrayObjectPool<>(ARRAY_STRIPES * ARRAY_STRIPE_SIZE, JsonArray.class);
        this.stringPool = new ArrayObjectPool<>(STRING_STRIPES * STRING_STRIPE_SIZE, JsonStringView.class);
        this.numberPool = new ArrayObjectPool<>(NUMBER_STRIPES * NUMBER_STRIPE_SIZE, JsonNumberView.class);
        this.slicePool = new ArrayObjectPool<>(SLICE_STRIPES * SLICE_STRIPE_SIZE, ByteSlice.class);
    }
    
    /**
     * Create view pools with custom sizes for HFT or other specialized use cases.
     * 
     * @param objectPoolSize size of the object pool
     * @param arrayPoolSize size of the array pool
     * @param stringPoolSize size of the string pool
     * @param numberPoolSize size of the number pool
     * @param slicePoolSize size of the slice pool
     */
    protected ViewPools(int objectPoolSize, int arrayPoolSize, int stringPoolSize, 
                      int numberPoolSize, int slicePoolSize) {
        this.objectPool = new ArrayObjectPool<>(objectPoolSize, JsonObject.class);
        this.arrayPool = new ArrayObjectPool<>(arrayPoolSize, JsonArray.class);
        this.stringPool = new ArrayObjectPool<>(stringPoolSize, JsonStringView.class);
        this.numberPool = new ArrayObjectPool<>(numberPoolSize, JsonNumberView.class);
        this.slicePool = new ArrayObjectPool<>(slicePoolSize, ByteSlice.class);
    }
    
    /**
     * Borrow a JsonObject from the pool.
     */
    public JsonObject borrowObject() {
        return objectPool.get();
    }
    public void returnObject(JsonObject obj) {
        if (obj != null) {
            objectPool.release(obj);
        }
    }
    
    /**
     * Borrow a JsonArray from the pool.
     */
    public JsonArray borrowArray() {
        return arrayPool.get();
    }
    public void returnArray(JsonArray arr) {
        if (arr != null) {
            arrayPool.release(arr);
        }
    }
    
    /**
     * Borrow a JsonStringView from the pool.
     */
    public JsonStringView borrowString() {
        return stringPool.get();
    }
    public void returnString(JsonStringView str) {
        if (str != null) {
            stringPool.release(str);
        }
    }
    
    /**
     * Borrow a JsonNumberView from the pool.
     */
    public JsonNumberView borrowNumber() {
        return numberPool.get();
    }
    public void returnNumber(JsonNumberView num) {
        if (num != null) {
            numberPool.release(num);
        }
    }
    
    /**
     * Borrow a ByteSlice from the pool.
     * The slice is returned uninitialized - caller must call reset().
     */
    public ByteSlice borrowSlice() {
        return slicePool.get();
    }
    
    /**
     * Borrow a ByteSlice from the pool and initialize it.
     * 
     * @param source the source byte array
     * @param offset the starting offset
     * @param length the length of the slice
     * @return a pooled and initialized ByteSlice
     */
    public ByteSlice borrowSlice(byte[] source, int offset, int length) {
        ByteSlice slice = slicePool.get();
        slice.reset(source, offset, length);
        return slice;
    }
    
    /**
     * Return a ByteSlice back to the pool.
     */
    public void returnSlice(ByteSlice slice) {
        if (slice != null) {
            slicePool.release(slice);
        }
    }
    
    /**
     * Return a view object back to the pool.
     * Automatically routes to the correct pool based on type.
     */
    public void returnView(JsonValue view) {
        if (view == null) {
            return;
        }
        
        if (view instanceof JsonObject) {
            objectPool.release((JsonObject) view);
        } else if (view instanceof JsonArray) {
            arrayPool.release((JsonArray) view);
        } else if (view instanceof JsonStringView) {
            stringPool.release((JsonStringView) view);
        } else if (view instanceof JsonNumberView) {
            numberPool.release((JsonNumberView) view);
        }
        // Singletons (JsonBoolean, JsonNull) don't need to be pooled
    }
    
    /**
     * Return all views recursively (including nested objects/arrays).
     * Use this for manual cleanup if not using JsonParseContext.
     */
    public void returnAll(JsonValue view) {
        if (view == null) {
            return;
        }
        
        if (view instanceof JsonObject) {
            JsonObject obj = (JsonObject) view;
            // Return all field values first
            for (java.util.Map.Entry<ByteSlice, JsonValue> entry : obj) {
                returnAll(entry.getValue());
            }
            objectPool.release(obj);
        } else if (view instanceof JsonArray) {
            JsonArray arr = (JsonArray) view;
            // Return all element values first
            for (JsonValue element : arr) {
                returnAll(element);
            }
            arrayPool.release(arr);
        } else if (view instanceof JsonStringView) {
            stringPool.release((JsonStringView) view);
        } else if (view instanceof JsonNumberView) {
            numberPool.release((JsonNumberView) view);
        }
    }
}

