package com.suko.zeroparse;

/**
 * InputCursor implementation for byte arrays.
 * 
 * <p>This implementation provides efficient access to byte array data
 * with zero-copy slice operations.</p>
 */
public final class ByteArrayCursor implements InputCursor {
    
    private final byte[] data;
    private final int offset;
    private final int length;
    
    /**
     * Create a new ByteArrayCursor for the entire array.
     * 
     * @param data the byte array to read from
     */
    public ByteArrayCursor(byte[] data) {
        this(data, 0, data.length);
    }
    
    /**
     * Create a new ByteArrayCursor for a portion of the array.
     * 
     * @param data the byte array to read from
     * @param offset the starting offset
     * @param length the length to read
     */
    public ByteArrayCursor(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        this.data = data;
        this.offset = offset;
        this.length = length;
    }
    
    @Override
    public int length() {
        return length;
    }
    
    @Override
    public byte byteAt(int index) {
        // Direct array access - bounds checking by JVM for maximum performance
        return data[offset + index];
    }
    
    @Override
    public char charAt(int index) {
        // Direct array access with proper unsigned byte conversion
        return (char) (data[offset + index] & 0xFF);
    }
    
    @Override
    public Utf8Slice slice(int start, int sliceLength) {
        if (start < 0 || sliceLength < 0 || start + sliceLength > length) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + sliceLength + ", cursorLength=" + length);
        }
        return new Utf8Slice(data, offset + start, sliceLength);
    }
    
    /**
     * Get the underlying byte array.
     * 
     * @return the byte array
     */
    public byte[] getData() {
        return data;
    }
    
    /**
     * Get the offset within the underlying array.
     * 
     * @return the offset
     */
    public int getOffset() {
        return offset;
    }
}
