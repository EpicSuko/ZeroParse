package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;
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
public class CursorPools {
    
    private static final int BUFFER_POOL_SIZE = 32;
    private static final int BYTE_BUF_POOL_SIZE = 32;
    private static final int BYTE_ARRAY_POOL_SIZE = 64;
    private static final int STRING_POOL_SIZE = 32;
    
    private final ObjectPool<BufferCursor> bufferPool;
    private final ObjectPool<ByteBufCursor> byteBufPool;
    private final ObjectPool<ByteArrayCursor> byteArrayPool;
    private final ObjectPool<StringCursor> stringPool;
    
    public CursorPools() {
        this.bufferPool = new ArrayObjectPool<>(BUFFER_POOL_SIZE, BufferCursor.class);
        this.byteBufPool = new ArrayObjectPool<>(BYTE_BUF_POOL_SIZE, ByteBufCursor.class);
        this.byteArrayPool = new ArrayObjectPool<>(BYTE_ARRAY_POOL_SIZE, ByteArrayCursor.class);
        this.stringPool = new ArrayObjectPool<>(STRING_POOL_SIZE, StringCursor.class);
    }
    
    /**
     * Create cursor pools with custom sizes for HFT or other specialized use cases.
     * 
     * @param bufferPoolSize size of the buffer cursor pool
     * @param byteBufPoolSize size of the ByteBuf cursor pool
     * @param byteArrayPoolSize size of the byte array cursor pool
     * @param stringPoolSize size of the string cursor pool
     */
    protected CursorPools(int bufferPoolSize, int byteBufPoolSize, int byteArrayPoolSize, int stringPoolSize) {
        this.bufferPool = new ArrayObjectPool<>(bufferPoolSize, BufferCursor.class);
        this.byteBufPool = new ArrayObjectPool<>(byteBufPoolSize, ByteBufCursor.class);
        this.byteArrayPool = new ArrayObjectPool<>(byteArrayPoolSize, ByteArrayCursor.class);
        this.stringPool = new ArrayObjectPool<>(stringPoolSize, StringCursor.class);
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
    
    public ByteBufCursor borrowByteBufCursor(ByteBuf byteBuf) {
        ByteBufCursor cursor = byteBufPool.get();
        cursor.reset(byteBuf);
        return cursor;
    }
    
    public void returnByteBufCursor(ByteBufCursor cursor) {
        if (cursor != null) {
            byteBufPool.release(cursor);
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

    public StringCursor borrowStringCursor(String data) {
        StringCursor cursor = stringPool.get();
        cursor.reset(data);
        return cursor;
    }

    public void returnStringCursor(StringCursor cursor) {
        if (cursor != null) {
            stringPool.release(cursor);
        }
    }
}

