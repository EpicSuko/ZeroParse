package com.suko.zeroparse;

import java.nio.ByteBuffer;

/**
 * OutputCursor implementation that writes to a Java NIO ByteBuffer.
 * 
 * <p>Supports both heap and direct ByteBuffers. The cursor uses the buffer's
 * current position as the write position and updates it as bytes are written.</p>
 * 
 * <p>The cursor is mutable and can be reset for reuse with different buffers,
 * making it suitable for object pooling.</p>
 */
public final class ByteBufferOutputCursor implements OutputCursor {
    
    private ByteBuffer buffer;
    private int startPosition;
    
    /**
     * Create a new ByteBufferOutputCursor for the given buffer.
     * Writing starts at the buffer's current position.
     * 
     * @param buffer the ByteBuffer to write to
     */
    public ByteBufferOutputCursor(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startPosition = buffer.position();
    }
    
    /**
     * Default constructor for object pooling.
     */
    public ByteBufferOutputCursor() {
        this.buffer = null;
        this.startPosition = 0;
    }
    
    /**
     * Reset this cursor to use a different buffer.
     * 
     * @param buffer the new buffer to write to
     */
    public void reset(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startPosition = buffer.position();
    }
    
    @Override
    public void writeByte(byte b) {
        buffer.put(b);
    }
    
    @Override
    public void writeBytes(byte[] bytes, int offset, int length) {
        buffer.put(bytes, offset, length);
    }
    
    @Override
    public int position() {
        return buffer.position() - startPosition;
    }
    
    @Override
    public void reset() {
        buffer.position(startPosition);
    }
    
    @Override
    public int capacity() {
        return buffer.capacity() - startPosition;
    }
    
    @Override
    public void ensureCapacity(int required) {
        int available = buffer.remaining();
        if (available < required - position()) {
            throw new IllegalStateException(
                "ByteBuffer capacity exceeded: required=" + required + 
                ", available=" + (position() + available));
        }
    }
    
    @Override
    public byte[] getBuffer() {
        if (buffer.hasArray()) {
            return buffer.array();
        }
        return null;
    }
    
    @Override
    public byte[] toBytes() {
        int len = position();
        byte[] result = new byte[len];
        
        // Save current position
        int currentPos = buffer.position();
        
        // Read from start position
        buffer.position(startPosition);
        buffer.get(result, 0, len);
        
        // Restore position
        buffer.position(currentPos);
        
        return result;
    }
    
    /**
     * Get the underlying ByteBuffer.
     * 
     * @return the ByteBuffer
     */
    public ByteBuffer getByteBuffer() {
        return buffer;
    }
    
    /**
     * Get the remaining capacity in the buffer.
     * 
     * @return the number of bytes that can still be written
     */
    public int remaining() {
        return buffer.remaining();
    }
    
    /**
     * Flip the buffer for reading.
     * Sets limit to current position and position to start.
     */
    public void flip() {
        buffer.limit(buffer.position());
        buffer.position(startPosition);
    }
}

