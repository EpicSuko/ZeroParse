package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;

/**
 * Reusable context for zero-allocation JSON serialization.
 * 
 * <p>This context manages a reusable JsonWriter and output buffer.
 * Create one context per Vert.x verticle or single-threaded environment
 * and reuse it across requests for zero-garbage serialization.</p>
 * 
 * <p><strong>Usage Example (internal buffer):</strong></p>
 * <pre>
 * // Create once per verticle
 * JsonSerializeContext ctx = new JsonSerializeContext();
 * 
 * // For each serialization:
 * ctx.reset();
 * JsonWriter writer = ctx.writer();
 * writer.objectStart();
 * writer.field("symbol", "BTCUSDT");
 * writer.field("price", 27000.50);
 * writer.objectEnd();
 * byte[] result = ctx.toBytes();
 * </pre>
 * 
 * <p><strong>Usage Example (external buffer, zero-copy):</strong></p>
 * <pre>
 * ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
 * ctx.reset(buffer);
 * JsonWriter writer = ctx.writer();
 * writer.objectStart();
 * // ... serialize data ...
 * writer.objectEnd();
 * // Data is now directly in buffer, no copy needed
 * </pre>
 * 
 * <p><strong>Fluent API:</strong></p>
 * <pre>
 * ctx.reset();
 * ctx.builder()
 *     .objectStart()
 *     .field("symbol", "BTCUSDT")
 *     .field("price", 27000.50)
 *     .end();
 * byte[] result = ctx.toBytes();
 * </pre>
 * 
 * <p><strong>Instance-based Design:</strong> This class uses no static or
 * ThreadLocal state, making it safe to use with multiple Vert.x verticles
 * in the same JVM. Each verticle should create its own context.</p>
 */
public final class JsonSerializeContext {
    
    private static final int DEFAULT_BUFFER_CAPACITY = 1024;
    
    // Internal output cursor (reused when not using external buffer)
    private final ByteArrayOutputCursor internalOutput;
    
    // The current output cursor (either internal or external)
    private OutputCursor currentOutput;
    
    // Reusable writer instance
    private final JsonWriter writer;
    
    // Reusable builder instance (lazily initialized)
    private JsonBuilder builder;
    
    // Cursor instances for external buffer types (reused to avoid allocation)
    private ByteBufferOutputCursor byteBufferCursor;
    private BufferOutputCursor bufferCursor;
    private ByteBufOutputCursor byteBufCursor;
    
    /**
     * Create a new serialization context with default buffer capacity.
     */
    public JsonSerializeContext() {
        this(DEFAULT_BUFFER_CAPACITY);
    }
    
    /**
     * Create a new serialization context with specified initial buffer capacity.
     * 
     * @param initialCapacity the initial internal buffer capacity
     */
    public JsonSerializeContext(int initialCapacity) {
        this.internalOutput = new ByteArrayOutputCursor(initialCapacity);
        this.currentOutput = internalOutput;
        this.writer = new JsonWriter(internalOutput);
        this.builder = null;
    }
    
    // ========== Reset Methods ==========
    
    /**
     * Reset the context for reuse with the internal buffer.
     * Clears any written content and resets state.
     */
    public void reset() {
        internalOutput.reset();
        currentOutput = internalOutput;
        writer.reset(internalOutput);
    }
    
    /**
     * Reset the context to write to an external byte array.
     * 
     * @param buffer the external byte array to write to
     */
    public void reset(byte[] buffer) {
        internalOutput.reset(buffer);
        currentOutput = internalOutput;
        writer.reset(internalOutput);
    }
    
    /**
     * Reset the context to write to an external byte array at an offset.
     * 
     * @param buffer the external byte array
     * @param offset the starting offset
     */
    public void reset(byte[] buffer, int offset) {
        internalOutput.reset(buffer, offset);
        currentOutput = internalOutput;
        writer.reset(internalOutput);
    }
    
    /**
     * Reset the context to write to a ByteBuffer.
     * 
     * @param buffer the ByteBuffer to write to
     */
    public void reset(ByteBuffer buffer) {
        if (byteBufferCursor == null) {
            byteBufferCursor = new ByteBufferOutputCursor(buffer);
        } else {
            byteBufferCursor.reset(buffer);
        }
        currentOutput = byteBufferCursor;
        writer.reset(byteBufferCursor);
    }
    
    /**
     * Reset the context to write to a Vert.x Buffer.
     * 
     * @param buffer the Vert.x Buffer to write to
     */
    public void reset(Buffer buffer) {
        if (bufferCursor == null) {
            bufferCursor = new BufferOutputCursor(buffer);
        } else {
            bufferCursor.reset(buffer);
        }
        currentOutput = bufferCursor;
        writer.reset(bufferCursor);
    }
    
    /**
     * Reset the context to write to a Netty ByteBuf.
     * 
     * @param buffer the ByteBuf to write to
     */
    public void reset(ByteBuf buffer) {
        if (byteBufCursor == null) {
            byteBufCursor = new ByteBufOutputCursor(buffer);
        } else {
            byteBufCursor.reset(buffer);
        }
        currentOutput = byteBufCursor;
        writer.reset(byteBufCursor);
    }
    
    // ========== Writer Access ==========
    
    /**
     * Get the reusable JsonWriter for streaming serialization.
     * 
     * @return the JsonWriter
     */
    public JsonWriter writer() {
        return writer;
    }
    
    /**
     * Get a fluent JsonBuilder for chainable serialization.
     * The builder wraps the internal writer.
     * 
     * @return the JsonBuilder
     */
    public JsonBuilder builder() {
        if (builder == null) {
            builder = new JsonBuilder(currentOutput);
        } else {
            // Update the builder to use current output
            builder.reset();
        }
        return builder;
    }
    
    // ========== Output Methods ==========
    
    /**
     * Get the written JSON as a byte array.
     * This allocates a new array sized to the written content.
     * 
     * @return a new byte array containing the JSON
     */
    public byte[] toBytes() {
        return currentOutput.toBytes();
    }
    
    /**
     * Copy the written JSON to a destination array.
     * 
     * @param dest the destination array
     * @param destOffset the starting offset in the destination
     * @return the number of bytes copied
     */
    public int copyTo(byte[] dest, int destOffset) {
        return currentOutput.copyTo(dest, destOffset);
    }
    
    /**
     * Get the number of bytes written.
     * 
     * @return the size in bytes
     */
    public int size() {
        return currentOutput.position();
    }
    
    /**
     * Get the current output cursor.
     * 
     * @return the OutputCursor
     */
    public OutputCursor output() {
        return currentOutput;
    }
    
    // ========== Direct Buffer Access ==========
    
    /**
     * Get the internal buffer (only valid when using internal output).
     * 
     * @return the internal byte array buffer
     */
    public byte[] getInternalBuffer() {
        return internalOutput.getBuffer();
    }
    
    /**
     * Get the ByteBuffer (only valid when reset(ByteBuffer) was called).
     * 
     * @return the ByteBuffer, or null if not using ByteBuffer output
     */
    public ByteBuffer getByteBuffer() {
        return byteBufferCursor != null ? byteBufferCursor.getByteBuffer() : null;
    }
    
    /**
     * Get the Vert.x Buffer (only valid when reset(Buffer) was called).
     * 
     * @return the Vert.x Buffer, or null if not using Buffer output
     */
    public Buffer getVertxBuffer() {
        return bufferCursor != null ? bufferCursor.getVertxBuffer() : null;
    }
    
    /**
     * Get the ByteBuf (only valid when reset(ByteBuf) was called).
     * 
     * @return the ByteBuf, or null if not using ByteBuf output
     */
    public ByteBuf getByteBuf() {
        return byteBufCursor != null ? byteBufCursor.getByteBuf() : null;
    }
    
    // ========== Convenience Methods ==========
    
    /**
     * Start writing a JSON object.
     * Convenience method that calls writer().objectStart().
     * 
     * @return this context for chaining
     */
    public JsonSerializeContext objectStart() {
        writer.objectStart();
        return this;
    }
    
    /**
     * End the current JSON object.
     * 
     * @return this context for chaining
     */
    public JsonSerializeContext objectEnd() {
        writer.objectEnd();
        return this;
    }
    
    /**
     * Start writing a JSON array.
     * 
     * @return this context for chaining
     */
    public JsonSerializeContext arrayStart() {
        writer.arrayStart();
        return this;
    }
    
    /**
     * End the current JSON array.
     * 
     * @return this context for chaining
     */
    public JsonSerializeContext arrayEnd() {
        writer.arrayEnd();
        return this;
    }
    
    /**
     * Write a string field.
     * 
     * @param name the field name
     * @param value the string value
     * @return this context for chaining
     */
    public JsonSerializeContext field(String name, String value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write an int field.
     * 
     * @param name the field name
     * @param value the int value
     * @return this context for chaining
     */
    public JsonSerializeContext field(String name, int value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a long field.
     * 
     * @param name the field name
     * @param value the long value
     * @return this context for chaining
     */
    public JsonSerializeContext field(String name, long value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a double field.
     * 
     * @param name the field name
     * @param value the double value
     * @return this context for chaining
     */
    public JsonSerializeContext field(String name, double value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a boolean field.
     * 
     * @param name the field name
     * @param value the boolean value
     * @return this context for chaining
     */
    public JsonSerializeContext field(String name, boolean value) {
        writer.field(name, value);
        return this;
    }
}

