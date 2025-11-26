package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;

/**
 * Fluent API wrapper around JsonWriter for ergonomic JSON serialization.
 * 
 * <p>Provides a chainable builder pattern for constructing JSON with minimal
 * boilerplate while maintaining zero-allocation guarantees.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * byte[] json = JsonBuilder.object()
 *     .field("symbol", "BTCUSDT")
 *     .field("price", 27000.50)
 *     .field("timestamp", System.currentTimeMillis())
 *     .array("bids")
 *         .value(27000.0).value(1.5)
 *     .endArray()
 * .end()
 * .toBytes();
 * </pre>
 * 
 * <p><strong>External Buffer Usage (zero-copy):</strong></p>
 * <pre>
 * ByteBuffer buffer = ByteBuffer.allocate(1024);
 * JsonBuilder.into(buffer)
 *     .object()
 *     .field("symbol", "BTCUSDT")
 *     .end();
 * // Data is now directly in buffer
 * </pre>
 * 
 * <p>The builder is reusable - call {@link #reset()} between uses.</p>
 */
public final class JsonBuilder {
    
    private final JsonWriter writer;
    
    /**
     * Create a new JsonBuilder with default buffer capacity.
     */
    public JsonBuilder() {
        this.writer = new JsonWriter();
    }
    
    /**
     * Create a new JsonBuilder with specified initial capacity.
     * 
     * @param initialCapacity the initial buffer capacity
     */
    public JsonBuilder(int initialCapacity) {
        this.writer = new JsonWriter(initialCapacity);
    }
    
    /**
     * Create a JsonBuilder that writes to an external output cursor.
     * 
     * @param output the external output cursor
     */
    public JsonBuilder(OutputCursor output) {
        this.writer = new JsonWriter(output);
    }
    
    /**
     * Create a JsonBuilder that writes to an external byte array.
     * 
     * @param buffer the external byte array
     */
    public JsonBuilder(byte[] buffer) {
        this.writer = new JsonWriter(buffer);
    }
    
    /**
     * Create a JsonBuilder that writes to a ByteBuffer.
     * 
     * @param buffer the ByteBuffer
     */
    public JsonBuilder(ByteBuffer buffer) {
        this.writer = new JsonWriter(buffer);
    }
    
    /**
     * Create a JsonBuilder that writes to a Vert.x Buffer.
     * 
     * @param buffer the Vert.x Buffer
     */
    public JsonBuilder(Buffer buffer) {
        this.writer = new JsonWriter(buffer);
    }
    
    /**
     * Create a JsonBuilder that writes to a Netty ByteBuf.
     * 
     * @param buffer the ByteBuf
     */
    public JsonBuilder(ByteBuf buffer) {
        this.writer = new JsonWriter(buffer);
    }
    
    // ========== Static Factory Methods ==========
    
    /**
     * Start building a JSON object.
     * 
     * @return a new JsonBuilder at object start
     */
    public static JsonBuilder object() {
        JsonBuilder builder = new JsonBuilder();
        builder.writer.objectStart();
        return builder;
    }
    
    /**
     * Start building a JSON array.
     * 
     * @return a new JsonBuilder at array start
     */
    public static JsonBuilder array() {
        JsonBuilder builder = new JsonBuilder();
        builder.writer.arrayStart();
        return builder;
    }
    
    /**
     * Create a builder that writes to an external byte array.
     * 
     * @param buffer the external byte array
     * @return a new JsonBuilder
     */
    public static JsonBuilder into(byte[] buffer) {
        return new JsonBuilder(buffer);
    }
    
    /**
     * Create a builder that writes to a ByteBuffer.
     * 
     * @param buffer the ByteBuffer
     * @return a new JsonBuilder
     */
    public static JsonBuilder into(ByteBuffer buffer) {
        return new JsonBuilder(buffer);
    }
    
    /**
     * Create a builder that writes to a Vert.x Buffer.
     * 
     * @param buffer the Vert.x Buffer
     * @return a new JsonBuilder
     */
    public static JsonBuilder into(Buffer buffer) {
        return new JsonBuilder(buffer);
    }
    
    /**
     * Create a builder that writes to a Netty ByteBuf.
     * 
     * @param buffer the ByteBuf
     * @return a new JsonBuilder
     */
    public static JsonBuilder into(ByteBuf buffer) {
        return new JsonBuilder(buffer);
    }
    
    // ========== Object/Array Structure ==========
    
    /**
     * Start a JSON object.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder objectStart() {
        writer.objectStart();
        return this;
    }
    
    /**
     * End the current JSON object.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder objectEnd() {
        writer.objectEnd();
        return this;
    }
    
    /**
     * Alias for objectEnd() - more readable in fluent chains.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder end() {
        if (writer.getDepth() > 0) {
            // Auto-detect whether we're in object or array based on last operation
            // For safety, just close whatever is open
            writer.objectEnd();
        }
        return this;
    }
    
    /**
     * Start a JSON array.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder arrayStart() {
        writer.arrayStart();
        return this;
    }
    
    /**
     * End the current JSON array.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder arrayEnd() {
        writer.arrayEnd();
        return this;
    }
    
    /**
     * Alias for arrayEnd() - more readable in fluent chains.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder endArray() {
        writer.arrayEnd();
        return this;
    }
    
    // ========== Field Methods (for objects) ==========
    
    /**
     * Write a string field.
     * 
     * @param name the field name
     * @param value the string value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, String value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write an int field.
     * 
     * @param name the field name
     * @param value the int value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, int value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a long field.
     * 
     * @param name the field name
     * @param value the long value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, long value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a double field.
     * 
     * @param name the field name
     * @param value the double value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, double value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a boolean field.
     * 
     * @param name the field name
     * @param value the boolean value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, boolean value) {
        writer.field(name, value);
        return this;
    }
    
    /**
     * Write a null field.
     * 
     * @param name the field name
     * @return this builder for chaining
     */
    public JsonBuilder fieldNull(String name) {
        writer.fieldNull(name);
        return this;
    }
    
    /**
     * Start a nested object field.
     * 
     * @param name the field name
     * @return this builder for chaining
     */
    public JsonBuilder object(String name) {
        writer.fieldName(name);
        writer.objectStart();
        return this;
    }
    
    /**
     * Start a nested array field.
     * 
     * @param name the field name
     * @return this builder for chaining
     */
    public JsonBuilder array(String name) {
        writer.fieldName(name);
        writer.arrayStart();
        return this;
    }
    
    /**
     * Write a field with a ByteSlice value (zero-copy from parsed JSON).
     * 
     * @param name the field name
     * @param value the ByteSlice value
     * @return this builder for chaining
     */
    public JsonBuilder field(String name, ByteSlice value) {
        writer.fieldName(name);
        writer.writeString(value);
        return this;
    }
    
    // ========== Value Methods (for arrays) ==========
    
    /**
     * Write a string value to the current array.
     * 
     * @param value the string value
     * @return this builder for chaining
     */
    public JsonBuilder value(String value) {
        writer.writeString(value);
        return this;
    }
    
    /**
     * Write an int value to the current array.
     * 
     * @param value the int value
     * @return this builder for chaining
     */
    public JsonBuilder value(int value) {
        writer.writeInt(value);
        return this;
    }
    
    /**
     * Write a long value to the current array.
     * 
     * @param value the long value
     * @return this builder for chaining
     */
    public JsonBuilder value(long value) {
        writer.writeLong(value);
        return this;
    }
    
    /**
     * Write a double value to the current array.
     * 
     * @param value the double value
     * @return this builder for chaining
     */
    public JsonBuilder value(double value) {
        writer.writeDouble(value);
        return this;
    }
    
    /**
     * Write a boolean value to the current array.
     * 
     * @param value the boolean value
     * @return this builder for chaining
     */
    public JsonBuilder value(boolean value) {
        writer.writeBoolean(value);
        return this;
    }
    
    /**
     * Write a null value to the current array.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder nullValue() {
        writer.writeNull();
        return this;
    }
    
    /**
     * Start a nested object in the current array.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder nestedObject() {
        writer.objectStart();
        return this;
    }
    
    /**
     * Start a nested array in the current array.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder nestedArray() {
        writer.arrayStart();
        return this;
    }
    
    // ========== Raw Writing ==========
    
    /**
     * Write raw bytes (pre-serialized JSON).
     * 
     * @param bytes the raw bytes
     * @return this builder for chaining
     */
    public JsonBuilder raw(byte[] bytes) {
        writer.writeRaw(bytes);
        return this;
    }
    
    /**
     * Write raw bytes from a ByteSlice.
     * 
     * @param slice the ByteSlice containing raw JSON
     * @return this builder for chaining
     */
    public JsonBuilder raw(ByteSlice slice) {
        writer.writeRaw(slice);
        return this;
    }
    
    // ========== Output Methods ==========
    
    /**
     * Reset the builder for reuse.
     * 
     * @return this builder for chaining
     */
    public JsonBuilder reset() {
        writer.reset();
        return this;
    }
    
    /**
     * Reset the builder to write to a different byte array.
     * 
     * @param buffer the new byte array
     * @return this builder for chaining
     */
    public JsonBuilder reset(byte[] buffer) {
        writer.reset(buffer);
        return this;
    }
    
    /**
     * Reset the builder to write to a different ByteBuffer.
     * 
     * @param buffer the new ByteBuffer
     * @return this builder for chaining
     */
    public JsonBuilder reset(ByteBuffer buffer) {
        writer.reset(buffer);
        return this;
    }
    
    /**
     * Reset the builder to write to a different Vert.x Buffer.
     * 
     * @param buffer the new Buffer
     * @return this builder for chaining
     */
    public JsonBuilder reset(Buffer buffer) {
        writer.reset(buffer);
        return this;
    }
    
    /**
     * Reset the builder to write to a different ByteBuf.
     * 
     * @param buffer the new ByteBuf
     * @return this builder for chaining
     */
    public JsonBuilder reset(ByteBuf buffer) {
        writer.reset(buffer);
        return this;
    }
    
    /**
     * Get the written JSON as a byte array.
     * 
     * @return a new byte array containing the JSON
     */
    public byte[] toBytes() {
        return writer.toBytes();
    }
    
    /**
     * Get the number of bytes written.
     * 
     * @return the size in bytes
     */
    public int size() {
        return writer.size();
    }
    
    /**
     * Get the underlying JsonWriter.
     * 
     * @return the JsonWriter
     */
    public JsonWriter getWriter() {
        return writer;
    }
    
    /**
     * Get the underlying OutputCursor.
     * 
     * @return the OutputCursor
     */
    public OutputCursor getOutput() {
        return writer.getOutput();
    }
}

