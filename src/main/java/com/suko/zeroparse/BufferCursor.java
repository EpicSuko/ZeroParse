package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;

/**
 * InputCursor implementation for Vert.x Buffer with zero-copy access.
 * 
 * <p>This implementation uses Vert.x BufferImpl internals to access the underlying byte array
 * without copying when possible. This is truly zero-copy for heap buffers.</p>
 * 
 * <p>The cursor is mutable and can be reset for reuse with different buffers,
 * making it suitable for object pooling.</p>
 */
public final class BufferCursor implements InputCursor {
    
    private Buffer buffer;
    private byte[] cachedBytes;
    private int offset;
    private int length;
    
    /**
     * Create a new BufferCursor.
     * 
     * @param buffer the Buffer to read from
     */
    public BufferCursor(Buffer buffer) {
        reset(buffer);
    }
    
    /**
     * Default constructor for object pooling.
     */
    BufferCursor() {
        this.buffer = null;
        this.cachedBytes = null;
        this.offset = 0;
        this.length = 0;
    }
    
    /**
     * Reset this cursor for reuse with a different buffer.
     * This enables zero-allocation parsing when using a cursor pool.
     * 
     * @param buffer the new Buffer to read from
     */
    public void reset(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.length = buffer.length();
        
        // Try to use Vert.x BufferImpl internals for zero-copy access
        try {
            if (buffer instanceof BufferImpl) {
                BufferImpl impl = (BufferImpl) buffer;
                io.netty.buffer.ByteBuf byteBuf = impl.byteBuf();
                if (byteBuf.hasArray()) {
                    // Heap buffer - we can access the underlying array without copying!
                    this.cachedBytes = byteBuf.array();
                    this.offset = byteBuf.arrayOffset() + byteBuf.readerIndex();
                } else {
                    // Direct buffer - fall back to copying (rare in typical usage)
                    this.cachedBytes = buffer.getBytes();
                    this.offset = 0;
                }
            } else {
                // Unknown buffer implementation - fall back to copying
                this.cachedBytes = buffer.getBytes();
                this.offset = 0;
            }
        } catch (Exception e) {
            // Fallback if ByteBuf access fails
            this.cachedBytes = buffer.getBytes();
            this.offset = 0;
        }
    }
    
    /**
     * Reset this cursor to null state (for pool reset action).
     */
    void reset() {
        this.buffer = null;
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
        // Direct array access with offset - much faster than buffer.getByte(index)
        // Bounds checking is done by the JVM
        return cachedBytes[offset + index];
    }
    
    @Override
    public char charAt(int index) {
        // Direct array access with proper unsigned byte conversion
        return (char) (cachedBytes[offset + index] & 0xFF);
    }
    
    @Override
    public Utf8Slice slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > this.length) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + length + ", bufferLength=" + this.length);
        }
        
        // Use pooled slice when in a parse context, otherwise allocate
        JsonParseContext ctx = JsonParseContext.getActiveContext();
        if (ctx != null) {
            return ctx.borrowSlice(cachedBytes, offset + start, length);
        }
        
        // Zero-copy slice using the cached byte array with offset
        return new Utf8Slice(cachedBytes, offset + start, length);
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
     * Get the underlying Buffer.
     * 
     * @return the Buffer
     */
    public Buffer getBuffer() {
        return buffer;
    }
    
    /**
     * Get the cached byte array backing this cursor.
     * 
     * @return the byte array
     */
    public byte[] getCachedBytes() {
        return cachedBytes;
    }
}
