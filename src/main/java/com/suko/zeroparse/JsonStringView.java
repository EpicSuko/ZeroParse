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
    }
    
    /**
     * Create a JsonStringView from a direct slice (for substrings).
     */
    public JsonStringView(Utf8Slice slice) {
        this.astStore = null;
        this.nodeIndex = -1;
        this.cursor = null;
        this.directSlice = slice;
    }
    
    /**
     * Default constructor for object pooling.
     */
    JsonStringView() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.directSlice = null;
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
    }
    
    /**
     * Reset this string view to null state (for pool reset action).
     */
    void reset() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.directSlice = null;
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
}

