package com.suko.zeroparse;

/**
 * Abstract interface for writing JSON output to various destinations.
 * 
 * <p>This interface provides a unified way to write JSON data to different
 * output types (byte[], ByteBuffer, Vert.x Buffer, Netty ByteBuf) while 
 * maintaining zero-copy semantics where possible.</p>
 * 
 * <p>Implementations are mutable and can be reset for reuse, making them
 * suitable for object pooling in single-threaded contexts.</p>
 */
public interface OutputCursor {
    
    /**
     * Write a single byte to the output.
     * 
     * @param b the byte to write
     */
    void writeByte(byte b);
    
    /**
     * Write multiple bytes from a byte array.
     * 
     * @param bytes the source byte array
     * @param offset the starting offset in the source array
     * @param length the number of bytes to write
     */
    void writeBytes(byte[] bytes, int offset, int length);
    
    /**
     * Write all bytes from a byte array.
     * 
     * @param bytes the source byte array
     */
    default void writeBytes(byte[] bytes) {
        writeBytes(bytes, 0, bytes.length);
    }
    
    /**
     * Write bytes from a ByteSlice (zero-copy from parsed JSON).
     * 
     * @param slice the source ByteSlice
     */
    default void writeBytes(ByteSlice slice) {
        writeBytes(slice.getSource(), slice.getOffset(), slice.getLength());
    }
    
    /**
     * Get the current write position.
     * 
     * @return the number of bytes written so far
     */
    int position();
    
    /**
     * Reset the cursor for reuse.
     * This clears the position but retains the underlying buffer.
     */
    void reset();
    
    /**
     * Get the current capacity of the underlying buffer.
     * 
     * @return the capacity in bytes
     */
    int capacity();
    
    /**
     * Ensure the buffer has at least the specified capacity.
     * May grow the buffer if needed.
     * 
     * @param required the minimum required capacity
     */
    void ensureCapacity(int required);
    
    /**
     * Get direct access to the underlying byte array (if available).
     * 
     * @return the underlying byte array, or null if not backed by a byte array
     */
    byte[] getBuffer();
    
    /**
     * Get the written bytes as a new byte array.
     * This allocates a new array sized exactly to the written content.
     * 
     * @return a new byte array containing the written bytes
     */
    default byte[] toBytes() {
        int len = position();
        byte[] result = new byte[len];
        byte[] buffer = getBuffer();
        if (buffer != null) {
            System.arraycopy(buffer, 0, result, 0, len);
        }
        return result;
    }
    
    /**
     * Copy the written bytes to a destination array.
     * 
     * @param dest the destination array
     * @param destOffset the starting offset in the destination
     * @return the number of bytes copied
     */
    default int copyTo(byte[] dest, int destOffset) {
        int len = position();
        byte[] buffer = getBuffer();
        if (buffer != null) {
            System.arraycopy(buffer, 0, dest, destOffset, len);
        }
        return len;
    }
}

