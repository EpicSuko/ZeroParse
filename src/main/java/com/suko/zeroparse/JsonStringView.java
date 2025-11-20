package com.suko.zeroparse;

import com.suko.zeroparse.stack.AstStore;

/**
 * A JSON string with lazy, zero-copy access backed by an AST.
 * 
 * <p>This class uses AST-backed lazy evaluation for efficient string
 * access without materializing the string upfront.</p>
 */
public final class JsonStringView implements JsonValue {
    
    // AST backing (mutable for pooling)
    protected AstStore astStore;
    protected int nodeIndex;
    protected InputCursor cursor;
    
    // Direct slice constructor for substring operations
    private Utf8Slice directSlice;
    
    // Cached parsed number values (for quoted numbers like {"price": "27000.5"})
    private long cachedLong;
    private double cachedDouble;
    private boolean hasLongCache;
    private boolean hasDoubleCache;
    
    /**
     * Create a new JsonStringView backed by AST.
     * 
     * @param astStore the AST store
     * @param nodeIndex the node index
     * @param cursor the input cursor
     */
    public JsonStringView(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Create a JsonStringView from a direct slice (for substrings).
     */
    public JsonStringView(Utf8Slice slice) {
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
    public JsonStringView() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.directSlice = null;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Reset this string view for reuse from the pool.
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

    void reset(Utf8Slice slice) {
        this.astStore = null;
        this.nodeIndex = -1;
        this.cursor = null;
        this.directSlice = slice;
        this.hasLongCache = false;
        this.hasDoubleCache = false;
    }
    
    /**
     * Reset this string view to null state (for pool reset action).
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
        return JsonType.STRING;
    }
    
    @Override
    public JsonStringView asString() {
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
    
    public int byteLength() {
        if (directSlice != null) {
            return directSlice.getLength();
        }
        return astStore.getEnd(nodeIndex) - astStore.getStart(nodeIndex);
    }
    
    public byte byteAt(int index) {
        if (directSlice != null) {
            return directSlice.byteAt(index);
        }
        int start = astStore.getStart(nodeIndex);
        return cursor.byteAt(start + index);
    }
    
    public boolean isEmpty() {
        return byteLength() == 0;
    }
    
    public JsonStringView subString(int start, int length) {
        Utf8Slice slice = slice();
        if (start == 0 && length == slice.getLength()) {
            return this;
        }
        byte[] source = slice.getSource();
        int offset = slice.getOffset() + start;
        return new JsonStringView(new Utf8Slice(source, offset, length));
    }
    
    public boolean equals(JsonStringView other) {
        if (other == null) {
            return false;
        }
        return slice().equals(other.slice());
    }
    
    public boolean equals(String str) {
        if (str == null) {
            return false;
        }
        return toString().equals(str);
    }
    
    /**
     * Compare this string view with a byte array without any allocations.
     * 
     * <p>This method performs a direct byte-by-byte comparison without creating
     * any intermediate objects, making it ideal for high-throughput scenarios.</p>
     * 
     * <p>Note: This compares the raw JSON string bytes, not the decoded value.
     * For escaped strings, this means comparing against the escaped form.</p>
     * 
     * @param bytes the byte array to compare against
     * @param offset the starting offset in the byte array
     * @param length the number of bytes to compare
     * @return true if the bytes match exactly, false otherwise
     */
    public boolean equals(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            return false;
        }
        
        // Quick length check first
        if (length != byteLength()) {
            return false;
        }
        
        // Compare byte by byte
        if (directSlice != null) {
            // Direct slice case - compare against the slice
            byte[] sliceBytes = directSlice.getSource();
            int sliceOffset = directSlice.getOffset();
            for (int i = 0; i < length; i++) {
                if (sliceBytes[sliceOffset + i] != bytes[offset + i]) {
                    return false;
                }
            }
        } else {
            // AST-backed case - use cursor to access bytes
            int start = astStore.getStart(nodeIndex);
            for (int i = 0; i < length; i++) {
                if (cursor.byteAt(start + i) != bytes[offset + i]) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Compare this string view with a byte array without any allocations.
     * 
     * <p>Convenience method that compares against the entire byte array.</p>
     * 
     * @param bytes the byte array to compare against
     * @return true if the bytes match exactly, false otherwise
     */
    public boolean equals(byte[] bytes) {
        return bytes != null && equals(bytes, 0, bytes.length);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof JsonStringView) {
            return equals((JsonStringView) obj);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return slice().hashCode();
    }
    
    @Override
    public String toString() {
        Utf8Slice slice = slice();
        
        // Check if string has escape sequences
        if (astStore != null && astStore.hasFlag(nodeIndex, AstStore.FLAG_STRING_ESCAPED)) {
            return decodeEscapedString(slice);
        } else {
            return slice.toString();
        }
    }
    
    private String decodeEscapedString(Utf8Slice slice) {
        StringBuilder sb = new StringBuilder();
        int length = slice.getLength();
        
        for (int i = 0; i < length; i++) {
            byte b = slice.byteAt(i);
            
            if (b == '\\' && i + 1 < length) {
                i++; // Skip the backslash
                byte next = slice.byteAt(i);
                
                switch (next) {
                    case '"':  sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        // Unicode escape sequence
                        if (i + 4 < length) {
                            try {
                                String hex = new String(new byte[]{
                                    slice.byteAt(i + 1),
                                    slice.byteAt(i + 2),
                                    slice.byteAt(i + 3),
                                    slice.byteAt(i + 4)
                                });
                                int codePoint = Integer.parseInt(hex, 16);
                                sb.append((char) codePoint);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('\\').append((char) next);
                            }
                        } else {
                            sb.append('\\').append((char) next);
                        }
                        break;
                    default:
                        sb.append('\\').append((char) next);
                        break;
                }
            } else {
                sb.append((char) b);
            }
        }
        
        return sb.toString();
    }
    
    // ========== Zero-allocation number parsing methods ==========
    
    /**
     * Parse this string as a double without allocating an intermediate String.
     * 
     * <p>This is particularly useful for JSON APIs that quote numbers as strings,
     * such as: {"price": "27000.5", "volume": "8.760"}</p>
     * 
     * <p>This method provides zero-allocation parsing for high-throughput scenarios
     * like crypto trading WebSocket handlers.</p>
     * 
     * <p>Results are cached after first parse for zero-overhead repeated access.</p>
     * 
     * @return the parsed double value
     * @throws NumberFormatException if the string is not a valid number
     */
    public double parseDouble() {
        // Return cached value if available
        if (hasDoubleCache) {
            return cachedDouble;
        }
        
        // Parse and cache - use direct byte array access without creating slice
        if (directSlice != null) {
            cachedDouble = NumberParser.parseDouble(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
        } else {
            // Parse directly from cursor using underlying bytes (ZERO allocation!)
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            cachedDouble = NumberParser.parseDouble(bytes, offset, length);
        }
        hasDoubleCache = true;
        return cachedDouble;
    }
    
    /**
     * Parse this string as a float without allocating an intermediate String.
     * 
     * <p>Results are cached after first parse for zero-overhead repeated access.</p>
     * 
     * @return the parsed float value
     * @throws NumberFormatException if the string is not a valid number
     */
    public float parseFloat() {
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
    
    /**
     * Parse this string as a long without allocating an intermediate String.
     * 
     * <p>Results are cached after first parse for zero-overhead repeated access.</p>
     * 
     * @return the parsed long value
     * @throws NumberFormatException if the string is not a valid number or out of range
     */
    public long parseLong() {
        // Return cached value if available
        if (hasLongCache) {
            return cachedLong;
        }
        
        // Parse and cache - use direct byte array access
        if (directSlice != null) {
            cachedLong = NumberParser.parseLong(directSlice.getSource(), directSlice.getOffset(), directSlice.getLength());
        } else {
            int start = astStore.getStart(nodeIndex);
            int length = astStore.getEnd(nodeIndex) - start;
            byte[] bytes = cursor.getUnderlyingBytes();
            int offset = cursor.getUnderlyingOffset(start);
            cachedLong = NumberParser.parseLong(bytes, offset, length);
        }
        hasLongCache = true;
        return cachedLong;
    }
    
    /**
     * Parse this string as an int without allocating an intermediate String.
     * 
     * <p>Results are cached after first parse for zero-overhead repeated access.</p>
     * 
     * @return the parsed int value
     * @throws NumberFormatException if the string is not a valid number or out of range
     */
    public int parseInt() {
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
    
    /**
     * Parse this string as a BigDecimal.
     * 
     * <p>Note: This method allocates a String internally as BigDecimal requires it.
     * Use only when arbitrary precision is required.</p>
     * 
     * @return the parsed BigDecimal value
     * @throws NumberFormatException if the string is not a valid number
     */
    public java.math.BigDecimal parseBigDecimal() {
        Utf8Slice slice = slice();
        return NumberParser.parseBigDecimal(slice.getSource(), slice.getOffset(), slice.getLength());
    }
    
    /**
     * Parse this string as a BigInteger.
     * 
     * <p>Note: This method allocates a String internally as BigInteger requires it.
     * Use only when arbitrary precision is required.</p>
     * 
     * @return the parsed BigInteger value
     * @throws NumberFormatException if the string is not a valid number
     */
    public java.math.BigInteger parseBigInteger() {
        Utf8Slice slice = slice();
        return NumberParser.parseBigInteger(slice.getSource(), slice.getOffset(), slice.getLength());
    }
}

