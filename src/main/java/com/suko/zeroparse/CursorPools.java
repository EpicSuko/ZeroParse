package com.suko.zeroparse;

import io.netty.util.concurrent.FastThreadLocal;
import io.vertx.core.buffer.Buffer;

/**
 * Thread-local pools for InputCursor instances to eliminate cursor allocation overhead.
 * 
 * <p>Cursors are pooled per-thread using FastThreadLocal for maximum performance.
 * This eliminates the need to allocate a new cursor for every parse operation,
 * which was causing significant overhead (especially BufferCursor.getBytes() calls).</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Buffer parsing with pooled cursor
 * Buffer buffer = ...;
 * BufferCursor cursor = CursorPools.borrowBufferCursor(buffer);
 * try {
 *     JsonValue result = parser.parse(cursor);
 *     // use result...
 * } finally {
 *     CursorPools.returnBufferCursor(cursor);
 * }
 * }</pre>
 */
public final class CursorPools {
    
    // Thread-local pools for each cursor type
    private static final FastThreadLocal<BufferCursor> BUFFER_CURSOR_POOL = 
        new FastThreadLocal<BufferCursor>() {
            @Override
            protected BufferCursor initialValue() {
                return new BufferCursor();
            }
        };
    
    private static final FastThreadLocal<ByteArrayCursor> BYTE_ARRAY_CURSOR_POOL = 
        new FastThreadLocal<ByteArrayCursor>() {
            @Override
            protected ByteArrayCursor initialValue() {
                return new ByteArrayCursor();
            }
        };
    
    private CursorPools() {
        // Utility class
    }
    
    /**
     * Borrow a BufferCursor from the thread-local pool and reset it for the given buffer.
     * This avoids allocating a new cursor and calling buffer.getBytes() on every parse.
     * 
     * @param buffer the Buffer to read from
     * @return a pooled BufferCursor ready for use
     */
    public static BufferCursor borrowBufferCursor(Buffer buffer) {
        BufferCursor cursor = BUFFER_CURSOR_POOL.get();
        cursor.reset(buffer);
        return cursor;
    }
    
    /**
     * Return a BufferCursor to the thread-local pool.
     * The cursor will be automatically reused on the next borrow call in this thread.
     * 
     * @param cursor the cursor to return (can be null)
     */
    public static void returnBufferCursor(BufferCursor cursor) {
        // With FastThreadLocal, the cursor is already in the pool
        // No action needed - it will be reset on next borrow
    }
    
    /**
     * Borrow a ByteArrayCursor from the thread-local pool and reset it for the given array.
     * 
     * @param data the byte array to read from
     * @param offset the starting offset
     * @param length the length to read
     * @return a pooled ByteArrayCursor ready for use
     */
    public static ByteArrayCursor borrowByteArrayCursor(byte[] data, int offset, int length) {
        ByteArrayCursor cursor = BYTE_ARRAY_CURSOR_POOL.get();
        cursor.reset(data, offset, length);
        return cursor;
    }
    
    /**
     * Borrow a ByteArrayCursor from the thread-local pool for the entire array.
     * 
     * @param data the byte array to read from
     * @return a pooled ByteArrayCursor ready for use
     */
    public static ByteArrayCursor borrowByteArrayCursor(byte[] data) {
        return borrowByteArrayCursor(data, 0, data.length);
    }
    
    /**
     * Return a ByteArrayCursor to the thread-local pool.
     * 
     * @param cursor the cursor to return (can be null)
     */
    public static void returnByteArrayCursor(ByteArrayCursor cursor) {
        // With FastThreadLocal, the cursor is already in the pool
        // No action needed - it will be reset on next borrow
    }
}

