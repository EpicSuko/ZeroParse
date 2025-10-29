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
    
    // AST backing
    protected final AstStore astStore;
    protected final int nodeIndex;
    protected final InputCursor cursor;
    
    // Direct slice constructor
    private Utf8Slice directSlice;
    
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
    }
    
    /**
     * Create a JsonNumberView from a direct slice.
     */
    public JsonNumberView(Utf8Slice slice) {
        this.astStore = null;
        this.nodeIndex = -1;
        this.cursor = null;
        this.directSlice = slice;
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
        String str = slice().toString();
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            // Try parsing as double first, then convert
            double d = Double.parseDouble(str);
            if (d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
                throw new NumberFormatException("Number out of long range: " + str);
            }
            return (long) d;
        }
    }
    
    public int asInt() {
        long value = asLong();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new NumberFormatException("Number out of int range: " + slice().toString());
        }
        return (int) value;
    }
    
    public double asDouble() {
        String str = slice().toString();
        return Double.parseDouble(str);
    }
    
    public float asFloat() {
        String str = slice().toString();
        return Float.parseFloat(str);
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

