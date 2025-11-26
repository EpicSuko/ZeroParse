package com.suko.zeroparse;

import java.nio.charset.StandardCharsets;

/**
 * A zero-copy byte slice that references a portion of a source buffer.
 * 
 * <p>This class provides efficient access to byte data without copying.
 * The slice maintains a reference to the source buffer and stores only
 * the offset and length of the subsequence.</p>
 * 
 * <p>While this class can represent bytes in any encoding, it provides
 * convenience methods for UTF-8 decoding which is commonly used in JSON.</p>
 * 
 * <p>This class is immutable and thread-safe as long as the underlying
 * source buffer is not modified.</p>
 */
public final class ByteSlice {
    
    private byte[] source;
    private int offset;
    private int length;
    
    /**
     * Package-private constructor for pooled instances.
     */
    public ByteSlice() {
        this.source = null;
        this.offset = 0;
        this.length = 0;
    }
    
    /**
     * Create a new byte slice.
     * 
     * @param source the source byte array
     * @param offset the starting offset
     * @param length the length of the slice
     */
    public ByteSlice(byte[] source, int offset, int length) {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > source.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        this.source = source;
        this.offset = offset;
        this.length = length;
    }
    
    /**
     * Reset this slice for reuse from the pool.
     * Called by the pool's reset action.
     * 
     * @param source the source byte array
     * @param offset the starting offset
     * @param length the length of the slice
     */
    void reset(byte[] source, int offset, int length) {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > source.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        this.source = source;
        this.offset = offset;
        this.length = length;
    }
    
    /**
     * Reset this slice to null state (for pool reset action).
     */
    void reset() {
        this.source = null;
        this.offset = 0;
        this.length = 0;
    }
    
    /**
     * Get the source byte array.
     * 
     * @return the source array
     */
    public byte[] getSource() {
        return source;
    }
    
    /**
     * Get the offset within the source array.
     * 
     * @return the offset
     */
    public int getOffset() {
        return offset;
    }
    
    /**
     * Get the length of this slice.
     * 
     * @return the length
     */
    public int getLength() {
        return length;
    }
    
    /**
     * Get a byte at the specified position within this slice.
     * 
     * @param index the position within the slice (0-based)
     * @return the byte at that position
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public byte byteAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
        }
        return source[offset + index];
    }
    
    /**
     * Check if this slice is empty.
     * 
     * @return true if the length is 0
     */
    public boolean isEmpty() {
        return length == 0;
    }
    
    /**
     * Create a sub-slice of this slice.
     * 
     * @param start the starting position within this slice
     * @param len the length of the sub-slice
     * @return a new ByteSlice
     * @throws IndexOutOfBoundsException if start or len is invalid
     */
    public ByteSlice subSlice(int start, int len) {
        if (start < 0 || len < 0 || start + len > length) {
            throw new IndexOutOfBoundsException("Invalid sub-slice: start=" + start + ", len=" + len + ", length=" + length);
        }
        return new ByteSlice(source, offset + start, len);
    }
    
    /**
     * Decode this slice to a String using UTF-8 encoding.
     * 
     * <p>This method performs UTF-8 decoding and may allocate memory.
     * Use this only when you need the actual String representation.</p>
     * 
     * @return the decoded string
     */
    public String decode() {
        if (length == 0) {
            return "";
        }
        return new String(source, offset, length, StandardCharsets.UTF_8);
    }
    
    /**
     * Check if this slice equals another slice by comparing the underlying bytes.
     * 
     * @param other the other slice to compare
     * @return true if the slices contain the same bytes
     */
    public boolean equals(ByteSlice other) {
        if (other == null) {
            return false;
        }
        if (this.length != other.length) {
            return false;
        }
        if (this.source == other.source && this.offset == other.offset) {
            return true;
        }
        for (int i = 0; i < length; i++) {
            if (this.source[this.offset + i] != other.source[other.offset + i]) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return equals((ByteSlice) obj);
    }
    
    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < length; i++) {
            result = 31 * result + source[offset + i];
        }
        return result;
    }
    
    @Override
    public String toString() {
        if (length == 0) {
            return "";
        }
        return new String(source, offset, length, StandardCharsets.UTF_8);
    }
    
    /**
     * Get a debug string representation of this slice.
     * 
     * @return debug information about the slice
     */
    public String toDebugString() {
        return "ByteSlice{offset=" + offset + ", length=" + length + "}";
    }
}
