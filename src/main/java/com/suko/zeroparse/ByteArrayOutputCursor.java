package com.suko.zeroparse;

/**
 * OutputCursor implementation that writes to a pre-allocated byte array.
 * 
 * <p>This implementation provides efficient direct array access with automatic
 * buffer growth when needed. The cursor is mutable and can be reset for reuse,
 * making it suitable for object pooling.</p>
 * 
 * <p>For zero-allocation serialization, pre-size the buffer appropriately
 * or use an external buffer via the constructor.</p>
 */
public final class ByteArrayOutputCursor implements OutputCursor {
    
    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    
    private byte[] buffer;
    private int position;
    private final boolean externalBuffer;
    
    /**
     * Create a new ByteArrayOutputCursor with default initial capacity.
     */
    public ByteArrayOutputCursor() {
        this(DEFAULT_INITIAL_CAPACITY);
    }
    
    /**
     * Create a new ByteArrayOutputCursor with specified initial capacity.
     * 
     * @param initialCapacity the initial buffer capacity
     */
    public ByteArrayOutputCursor(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be positive");
        }
        this.buffer = new byte[initialCapacity];
        this.position = 0;
        this.externalBuffer = false;
    }
    
    /**
     * Create a ByteArrayOutputCursor that writes to an external buffer.
     * The buffer will NOT be grown if capacity is exceeded (throws exception).
     * 
     * @param externalBuffer the external buffer to write to
     */
    public ByteArrayOutputCursor(byte[] externalBuffer) {
        if (externalBuffer == null) {
            throw new IllegalArgumentException("External buffer cannot be null");
        }
        this.buffer = externalBuffer;
        this.position = 0;
        this.externalBuffer = true;
    }
    
    /**
     * Create a ByteArrayOutputCursor that writes to an external buffer starting at an offset.
     * 
     * @param externalBuffer the external buffer to write to
     * @param startOffset the starting position in the buffer
     */
    public ByteArrayOutputCursor(byte[] externalBuffer, int startOffset) {
        if (externalBuffer == null) {
            throw new IllegalArgumentException("External buffer cannot be null");
        }
        if (startOffset < 0 || startOffset > externalBuffer.length) {
            throw new IllegalArgumentException("Invalid start offset");
        }
        this.buffer = externalBuffer;
        this.position = startOffset;
        this.externalBuffer = true;
    }
    
    @Override
    public void writeByte(byte b) {
        ensureCapacity(position + 1);
        buffer[position++] = b;
    }
    
    @Override
    public void writeBytes(byte[] bytes, int offset, int length) {
        if (length == 0) {
            return;
        }
        ensureCapacity(position + length);
        System.arraycopy(bytes, offset, buffer, position, length);
        position += length;
    }
    
    @Override
    public int position() {
        return position;
    }
    
    @Override
    public void reset() {
        position = 0;
    }
    
    /**
     * Reset the cursor to a specific position.
     * Useful for external buffers where you want to append at a specific offset.
     * 
     * @param newPosition the new position
     */
    public void reset(int newPosition) {
        if (newPosition < 0 || newPosition > buffer.length) {
            throw new IllegalArgumentException("Invalid position");
        }
        this.position = newPosition;
    }
    
    /**
     * Reset this cursor to use a different external buffer.
     * 
     * @param newBuffer the new buffer to use
     */
    public void reset(byte[] newBuffer) {
        if (newBuffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = newBuffer;
        this.position = 0;
    }
    
    /**
     * Reset this cursor to use a different external buffer at a specific offset.
     * 
     * @param newBuffer the new buffer to use
     * @param startOffset the starting position
     */
    public void reset(byte[] newBuffer, int startOffset) {
        if (newBuffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        if (startOffset < 0 || startOffset > newBuffer.length) {
            throw new IllegalArgumentException("Invalid start offset");
        }
        this.buffer = newBuffer;
        this.position = startOffset;
    }
    
    @Override
    public int capacity() {
        return buffer.length;
    }
    
    @Override
    public void ensureCapacity(int required) {
        if (required <= buffer.length) {
            return;
        }
        
        if (externalBuffer) {
            throw new IllegalStateException(
                "External buffer capacity exceeded: required=" + required + 
                ", capacity=" + buffer.length);
        }
        
        // Grow the buffer
        int newCapacity = buffer.length;
        while (newCapacity < required) {
            newCapacity = newCapacity << 1;
            if (newCapacity < 0 || newCapacity > MAX_ARRAY_SIZE) {
                newCapacity = MAX_ARRAY_SIZE;
                break;
            }
        }
        
        if (newCapacity < required) {
            throw new OutOfMemoryError("Required capacity exceeds maximum array size");
        }
        
        byte[] newBuffer = new byte[newCapacity];
        System.arraycopy(buffer, 0, newBuffer, 0, position);
        buffer = newBuffer;
    }
    
    @Override
    public byte[] getBuffer() {
        return buffer;
    }
    
    /**
     * Check if this cursor is using an external buffer.
     * 
     * @return true if using an external buffer that won't be grown
     */
    public boolean isExternalBuffer() {
        return externalBuffer;
    }
    
    /**
     * Get the remaining capacity in the buffer.
     * 
     * @return the number of bytes that can still be written
     */
    public int remaining() {
        return buffer.length - position;
    }
}

