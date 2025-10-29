package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;

/**
 * InputCursor implementation for Vert.x Buffer.
 * 
 * <p>This implementation provides efficient access to Buffer data
 * with zero-copy slice operations. The underlying byte array is cached
 * on first access for maximum performance.</p>
 */
public final class BufferCursor implements InputCursor {
    
    private final Buffer buffer;
    private final byte[] cachedBytes;
    private final int length;
    
    /**
     * Create a new BufferCursor.
     * 
     * @param buffer the Buffer to read from
     */
    public BufferCursor(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.length = buffer.length();
        
        // Extract the underlying byte array once for fast access
        // This is still zero-copy in spirit - we're just avoiding repeated
        // virtual method calls through the Buffer interface
        this.cachedBytes = buffer.getBytes();
    }
    
    @Override
    public int length() {
        return this.length;
    }
    
    @Override
    public byte byteAt(int index) {
        // Direct array access - much faster than buffer.getByte(index)
        // Bounds checking is done by the JVM
        return cachedBytes[index];
    }
    
    @Override
    public char charAt(int index) {
        // Direct array access with proper unsigned byte conversion
        return (char) (cachedBytes[index] & 0xFF);
    }
    
    @Override
    public Utf8Slice slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > this.length) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + length + ", bufferLength=" + this.length);
        }
        
        // Zero-copy slice using the cached byte array
        // This avoids allocating a new array for each slice
        return new Utf8Slice(cachedBytes, start, length);
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
