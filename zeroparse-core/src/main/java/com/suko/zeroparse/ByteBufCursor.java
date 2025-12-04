package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;

/**
 * InputCursor implementation for Netty ByteBuf with zero-copy access.
 * 
 * <p>This implementation provides direct access to Netty ByteBuf data.
 * For heap buffers, it accesses the underlying array without copying.
 * For direct buffers, it copies to a reusable fallback array.</p>
 * 
 * <p>The cursor is mutable and can be reset for reuse with different buffers,
 * making it suitable for object pooling.</p>
 */
public final class ByteBufCursor implements InputCursor {
    
    private ByteBuf byteBuf;
    private byte[] cachedBytes;
    private int offset;
    private int length;
    // Optional parse context for pooled slice creation
    private JsonParseContext context;
    private byte[] fallbackBytes;
    
    /**
     * Create a new ByteBufCursor.
     * 
     * @param byteBuf the ByteBuf to read from
     */
    public ByteBufCursor(ByteBuf byteBuf) {
        reset(byteBuf);
    }
    
    /**
     * Default constructor for object pooling.
     */
    public ByteBufCursor() {
        this.byteBuf = null;
        this.cachedBytes = null;
        this.offset = 0;
        this.length = 0;
        this.fallbackBytes = null;
    }
    
    /**
     * Reset this cursor for reuse with a different ByteBuf.
     * This enables zero-allocation parsing when using a cursor pool.
     * 
     * @param byteBuf the new ByteBuf to read from
     */
    public void reset(ByteBuf byteBuf) {
        if (byteBuf == null) {
            throw new IllegalArgumentException("ByteBuf cannot be null");
        }
        this.byteBuf = byteBuf;
        this.length = byteBuf.readableBytes();
        
        if (byteBuf.hasArray()) {
            // Heap buffer - we can access the underlying array without copying!
            this.cachedBytes = byteBuf.array();
            this.offset = byteBuf.arrayOffset() + byteBuf.readerIndex();
        } else {
            // Direct buffer - need to copy to a byte array for fast access
            ensureFallbackCapacity(length);
            byteBuf.getBytes(byteBuf.readerIndex(), fallbackBytes, 0, length);
            this.cachedBytes = fallbackBytes;
            this.offset = 0;
        }
    }
    
    /**
     * Reset this cursor to null state (for pool reset action).
     */
    void reset() {
        this.byteBuf = null;
        this.cachedBytes = null;
        this.offset = 0;
        this.length = 0;
    }
    
    @Override
    public int length() {
        return this.length;
    }
    
    @Override
    public byte byteAt(int index) {
        // Direct array access with offset - much faster than byteBuf.getByte(index)
        // Bounds checking is done by the JVM
        return cachedBytes[offset + index];
    }
    
    @Override
    public char charAt(int index) {
        // Direct array access with proper unsigned byte conversion
        return (char) (cachedBytes[offset + index] & 0xFF);
    }
    
    @Override
    public ByteSlice slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > this.length) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + length + ", bufferLength=" + this.length);
        }
        
        // Use pooled slice when a context is attached, otherwise allocate
        JsonParseContext ctx = this.context;
        if (ctx != null) {
            return ctx.borrowSlice(cachedBytes, offset + start, length);
        }
        
        // Zero-copy slice using the cached byte array with offset
        return new ByteSlice(cachedBytes, offset + start, length);
    }
    
    @Override
    public byte[] getUnderlyingBytes() {
        return cachedBytes;
    }
    
    @Override
    public int getUnderlyingOffset(int position) {
        return offset + position;
    }
    
    /**
     * Get the underlying ByteBuf.
     * 
     * @return the ByteBuf
     */
    public ByteBuf getByteBuf() {
        return byteBuf;
    }
    
    /**
     * Get the cached byte array backing this cursor.
     * 
     * @return the byte array
     */
    public byte[] getCachedBytes() {
        return cachedBytes;
    }

    void setContext(JsonParseContext context) {
        this.context = context;
    }

    private void ensureFallbackCapacity(int required) {
        if (fallbackBytes == null || fallbackBytes.length < required) {
            int newSize = fallbackBytes == null ? 1024 : fallbackBytes.length;
            while (newSize < required) {
                newSize <<= 1;
            }
            fallbackBytes = new byte[newSize];
        }
    }
}

