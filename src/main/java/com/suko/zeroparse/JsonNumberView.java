package com.suko.zeroparse;

import com.suko.zeroparse.stack.AstStore;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A JSON number with lazy parsing backed by an AST.
 * 
 * <p>This class uses AST-backed lazy evaluation for efficient number
 * access without parsing the number upfront.</p>
 */
public final class JsonNumberView implements JsonValue {
    
    // AST backing (mutable for pooling)
    protected AstStore astStore;
    protected int nodeIndex;
    protected InputCursor cursor;
    
    // Direct slice constructor
    private Utf8Slice directSlice;
    
    // Cached parsed values (for repeated access)
    private long cachedLong;
    private double cachedDouble;
    private boolean hasLongCache;
    private boolean hasDoubleCache;
    
    /**
     * Create a new JsonNumberView backed by AST.
     * 
     * @param astStore the AST store
     * @param nodeIndex the node index
     * @param cursor the input cursor
     */
    public JsonNumberView(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Create a JsonNumberView from a direct slice.
     */
    public JsonNumberView(Utf8Slice slice) {
        this.astStore = null;
        this.nodeIndex = -1;
        this.cursor = null;
        this.directSlice = slice;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Default constructor for object pooling.
     */
    public JsonNumberView() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Reset this number view for reuse from the pool.
     * Called by the pool's reset action.
     */
    void reset(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Reset this number view to null state (for pool reset action).
     */
    void reset() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    @Override
    public JsonType getType() {
        return JsonType.NUMBER;
    }
    
    @Override
    public JsonNumberView asNumber() {
        return this;
    }
    
    public Utf8Slice slice() {
        if (directSlice != null) {
            return directSlice;
        }
        int start = astStore.getStart(nodeIndex);
        int length = astStore.getEnd(nodeIndex) - start;
        return cursor.slice(start, length);
    }
    
    public boolean isInteger() {
        if (astStore != null) {
            return !astStore.hasFlag(nodeIndex, AstStore.FLAG_NUMBER_FLOAT);
        }
        // Check if slice contains '.' or 'e'/'E'
        Utf8Slice slice = slice();
        for (int i = 0; i < slice.getLength(); i++) {
            byte b = slice.byteAt(i);
            if (b == '.' || b == 'e' || b == 'E') {
                return false;
            }
        }
        return true;
    }
    
    public boolean isNegative() {
        Utf8Slice slice = slice();
        return slice.getLength() > 0 && slice.byteAt(0) == '-';
    }
    
    public long asLong() {
        // Return cached value if available
        if (hasLongCache) {
            return cachedLong;
        }
        
        // Parse and cache - use direct byte array access without creating slice
        if (directSlice != null) {
            cachedLong = NumberParser.parseLong(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
        } else {
            // Parse directly from cursor using underlying bytes (ZERO allocation!)
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            cachedLong = NumberParser.parseLong(bytes, offset, length);
        }
        hasLongCache = true;
        return cachedLong;
    }
    
    public int asInt() {
        // Use cached long if available (avoids re-parsing)
        if (hasLongCache) {
            if (cachedLong < Integer.MIN_VALUE || cachedLong > Integer.MAX_VALUE) {
                throw new NumberFormatException("Number out of int range");
            }
            return (int) cachedLong;
        }
        
        // Parse as int directly - use direct byte array access
        int value;
        if (directSlice != null) {
            value = NumberParser.parseInt(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
        } else {
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            value = NumberParser.parseInt(bytes, offset, length);
        }
        
        // Cache as long for future use
        cachedLong = value;
        hasLongCache = true;
        
        return value;
    }
    
    public double asDouble() {
        // Return cached value if available
        if (hasDoubleCache) {
            return cachedDouble;
        }
        
        // Parse and cache - use direct byte array access
        if (directSlice != null) {
            cachedDouble = NumberParser.parseDouble(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
        } else {
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            cachedDouble = NumberParser.parseDouble(bytes, offset, length);
        }
        hasDoubleCache = true;
        return cachedDouble;
    }
    
    public float asFloat() {
        // Use cached double if available (avoids re-parsing)
        if (hasDoubleCache) {
            return (float) cachedDouble;
        }
        
        // Parse as float and cache as double - use direct byte array access
        if (directSlice != null) {
            float value = NumberParser.parseFloat(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
            cachedDouble = value;
        } else {
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            float value = NumberParser.parseFloat(bytes, offset, length);
            cachedDouble = value;
        }
        hasDoubleCache = true;
        return (float) cachedDouble;
    }
    
    public BigDecimal asBigDecimal() {
        String str = slice().toString();
        return new BigDecimal(str);
    }
    
    public BigInteger asBigInteger() {
        if (!isInteger()) {
            throw new NumberFormatException("Cannot convert non-integer to BigInteger: " + slice().toString());
        }
        String str = slice().toString();
        return new BigInteger(str);
    }
    
    public boolean equals(JsonNumberView other) {
        if (other == null) {
            return false;
        }
        return slice().equals(other.slice());
    }
    
    public boolean equals(Number number) {
        if (number == null) {
            return false;
        }
        try {
            if (isInteger()) {
                return asLong() == number.longValue();
            } else {
                return Double.compare(asDouble(), number.doubleValue()) == 0;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof JsonNumberView) {
            return equals((JsonNumberView) obj);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return slice().hashCode();
    }
    
    @Override
    public String toString() {
        return slice().toString();
    }
}

