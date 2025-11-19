package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import com.suko.pool.ArrayObjectPool;
import com.suko.pool.ObjectPool;

/**
 * Instance-scoped pools for InputCursor reuse.
 *
 * <p>Each {@link JsonParser} owns a {@code CursorPools} so cursors are reused
 * within the same single-threaded environment (e.g. Vert.x event loop) without
 * relying on ThreadLocals.</p>
 */
public final class CursorPools {
    
    private static final int BUFFER_POOL_SIZE = 32;
    private static final int BYTE_ARRAY_POOL_SIZE = 64;
    
    private final ObjectPool<BufferCursor> bufferPool;
    private final ObjectPool<ByteArrayCursor> byteArrayPool;
    
    public CursorPools() {
        this.bufferPool = new ArrayObjectPool<>(BUFFER_POOL_SIZE, BufferCursor.class);
        this.byteArrayPool = new ArrayObjectPool<>(BYTE_ARRAY_POOL_SIZE, ByteArrayCursor.class);
    }
    
    public BufferCursor borrowBufferCursor(Buffer buffer) {
        BufferCursor cursor = bufferPool.get();
        cursor.reset(buffer);
        return cursor;
    }
    
    public void returnBufferCursor(BufferCursor cursor) {
        if (cursor != null) {
            bufferPool.release(cursor);
        }
    }
    
    public ByteArrayCursor borrowByteArrayCursor(byte[] data, int offset, int length) {
        ByteArrayCursor cursor = byteArrayPool.get();
        cursor.reset(data, offset, length);
        return cursor;
    }
    
    public ByteArrayCursor borrowByteArrayCursor(byte[] data) {
        return borrowByteArrayCursor(data, 0, data.length);
    }
    
    public void returnByteArrayCursor(ByteArrayCursor cursor) {
        if (cursor != null) {
            byteArrayPool.release(cursor);
        }
    }
}

