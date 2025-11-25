package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;

/**
 * OutputCursor implementation that writes to a Vert.x Buffer.
 * 
 * <p>This implementation appends to the buffer, allowing it to grow as needed.
 * For zero-copy scenarios, you can pass an existing buffer that will be
 * appended to directly.</p>
 * 
 * <p>The cursor is mutable and can be reset for reuse with different buffers,
 * making it suitable for object pooling.</p>
 */
public final class BufferOutputCursor implements OutputCursor {
    
    private Buffer buffer;
    private int startLength;
    
    /**
     * Create a new BufferOutputCursor for the given buffer.
     * Writing appends to the buffer's current content.
     * 
     * @param buffer the Vert.x Buffer to write to
     */
    public BufferOutputCursor(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startLength = buffer.length();
    }
    
    /**
     * Default constructor for object pooling.
     */
    public BufferOutputCursor() {
        this.buffer = null;
        this.startLength = 0;
    }
    
    /**
     * Reset this cursor to use a different buffer.
     * 
     * @param buffer the new buffer to write to
     */
    public void reset(Buffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startLength = buffer.length();
    }
    
    @Override
    public void writeByte(byte b) {
        buffer.appendByte(b);
    }
    
    @Override
    public void writeBytes(byte[] bytes, int offset, int length) {
        buffer.appendBytes(bytes, offset, length);
    }
    
    @Override
    public int position() {
        return buffer.length() - startLength;
    }
    
    @Override
    public void reset() {
        // Vert.x Buffer doesn't support truncation, so we create a slice
        // For true zero-allocation, user should pass a fresh buffer
        if (startLength == 0) {
            // Replace with new empty buffer
            buffer = Buffer.buffer();
        } else {
            // Keep only the original content
            buffer = buffer.slice(0, startLength);
        }
    }
    
    @Override
    public int capacity() {
        // Vert.x Buffer grows dynamically
        return Integer.MAX_VALUE;
    }
    
    @Override
    public void ensureCapacity(int required) {
        // Vert.x Buffer grows dynamically, no need to pre-allocate
    }
    
    @Override
    public byte[] getBuffer() {
        return buffer.getBytes();
    }
    
    @Override
    public byte[] toBytes() {
        if (startLength == 0) {
            return buffer.getBytes();
        }
        return buffer.getBytes(startLength, buffer.length());
    }
    
    /**
     * Get the underlying Vert.x Buffer.
     * 
     * @return the Buffer
     */
    public Buffer getVertxBuffer() {
        return buffer;
    }
    
    /**
     * Get the current length of the buffer.
     * 
     * @return the total buffer length
     */
    public int length() {
        return buffer.length();
    }
}

