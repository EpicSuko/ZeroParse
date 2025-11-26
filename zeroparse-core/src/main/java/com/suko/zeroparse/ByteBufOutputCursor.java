package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;

/**
 * OutputCursor implementation that writes directly to a Netty ByteBuf.
 * 
 * <p>This implementation uses the ByteBuf's writer index for positioning.
 * Supports both heap and direct ByteBufs, and allows for zero-copy
 * writing when the ByteBuf is the final destination.</p>
 * 
 * <p>The cursor is mutable and can be reset for reuse with different buffers,
 * making it suitable for object pooling.</p>
 */
public final class ByteBufOutputCursor implements OutputCursor {
    
    private ByteBuf buffer;
    private int startWriterIndex;
    
    /**
     * Create a new ByteBufOutputCursor for the given buffer.
     * Writing starts at the buffer's current writer index.
     * 
     * @param buffer the ByteBuf to write to
     */
    public ByteBufOutputCursor(ByteBuf buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startWriterIndex = buffer.writerIndex();
    }
    
    /**
     * Default constructor for object pooling.
     */
    public ByteBufOutputCursor() {
        this.buffer = null;
        this.startWriterIndex = 0;
    }
    
    /**
     * Reset this cursor to use a different buffer.
     * 
     * @param buffer the new buffer to write to
     */
    public void reset(ByteBuf buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        this.buffer = buffer;
        this.startWriterIndex = buffer.writerIndex();
    }
    
    @Override
    public void writeByte(byte b) {
        buffer.writeByte(b);
    }
    
    @Override
    public void writeBytes(byte[] bytes, int offset, int length) {
        buffer.writeBytes(bytes, offset, length);
    }
    
    @Override
    public int position() {
        return buffer.writerIndex() - startWriterIndex;
    }
    
    @Override
    public void reset() {
        buffer.writerIndex(startWriterIndex);
    }
    
    @Override
    public int capacity() {
        return buffer.maxCapacity() - startWriterIndex;
    }
    
    @Override
    public void ensureCapacity(int required) {
        int neededCapacity = startWriterIndex + required;
        if (neededCapacity > buffer.maxCapacity()) {
            throw new IllegalStateException(
                "ByteBuf max capacity exceeded: required=" + neededCapacity + 
                ", maxCapacity=" + buffer.maxCapacity());
        }
        buffer.ensureWritable(required - position());
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
        buffer.getBytes(startWriterIndex, result);
        return result;
    }
    
    /**
     * Get the underlying ByteBuf.
     * 
     * @return the ByteBuf
     */
    public ByteBuf getByteBuf() {
        return buffer;
    }
    
    /**
     * Get the writable bytes remaining.
     * 
     * @return the number of bytes that can still be written
     */
    public int writableBytes() {
        return buffer.writableBytes();
    }
    
    /**
     * Get the max writable bytes.
     * 
     * @return the maximum number of bytes that can be written
     */
    public int maxWritableBytes() {
        return buffer.maxWritableBytes();
    }
}

