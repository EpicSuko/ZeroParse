package com.suko.zeroparse;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;

/**
 * Zero-allocation immediate-mode JSON writer.
 * 
 * <p>Provides a streaming API for writing JSON directly to output buffers
 * without creating intermediate String or Object allocations.</p>
 * 
 * <p>The writer is mutable and can be reset for reuse. In a Vert.x verticle,
 * create one writer per verticle and reuse it across requests.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * JsonWriter writer = new JsonWriter();
 * writer.objectStart();
 * writer.fieldName("symbol");
 * writer.writeString("BTCUSDT");
 * writer.fieldName("price");
 * writer.writeDouble(27000.50);
 * writer.objectEnd();
 * byte[] result = writer.toBytes();
 * </pre>
 * 
 * <p><strong>External Buffer Usage (zero-copy):</strong></p>
 * <pre>
 * ByteBuffer buffer = ByteBuffer.allocate(1024);
 * JsonWriter writer = new JsonWriter(buffer);
 * writer.objectStart();
 * // ... write JSON ...
 * writer.objectEnd();
 * // Data is now directly in buffer, no copy needed
 * </pre>
 */
public final class JsonWriter {
    
    // JSON syntax bytes (static final constants)
    private static final byte OBJECT_START = '{';
    private static final byte OBJECT_END = '}';
    private static final byte ARRAY_START = '[';
    private static final byte ARRAY_END = ']';
    private static final byte COLON = ':';
    private static final byte COMMA = ',';
    private static final byte QUOTE = '"';
    
    // Pre-computed literal bytes
    private static final byte[] TRUE_BYTES = { 't', 'r', 'u', 'e' };
    private static final byte[] FALSE_BYTES = { 'f', 'a', 'l', 's', 'e' };
    private static final byte[] NULL_BYTES = { 'n', 'u', 'l', 'l' };
    
    // Default initial capacity
    private static final int DEFAULT_CAPACITY = 256;
    
    // The output cursor
    private OutputCursor output;
    
    // State tracking
    private boolean needsComma;
    private int depth;
    
    // Scratch buffer for number formatting (allocated once, reused on every write)
    // This makes number serialization zero-allocation when reusing JsonWriter
    private final byte[] numberScratch = new byte[32];
    
    
    /**
     * Create a new JsonWriter with default initial buffer capacity.
     */
    public JsonWriter() {
        this(DEFAULT_CAPACITY);
    }
    
    /**
     * Create a new JsonWriter with specified initial buffer capacity.
     * 
     * @param initialCapacity the initial buffer capacity
     */
    public JsonWriter(int initialCapacity) {
        this.output = new ByteArrayOutputCursor(initialCapacity);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes to an external OutputCursor.
     * 
     * @param output the external output cursor
     */
    public JsonWriter(OutputCursor output) {
        this.output = output;
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes directly to a byte array.
     * 
     * @param buffer the external byte array
     */
    public JsonWriter(byte[] buffer) {
        this.output = new ByteArrayOutputCursor(buffer);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes directly to a byte array at an offset.
     * 
     * @param buffer the external byte array
     * @param offset the starting offset
     */
    public JsonWriter(byte[] buffer, int offset) {
        this.output = new ByteArrayOutputCursor(buffer, offset);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes directly to a Java NIO ByteBuffer.
     * 
     * @param buffer the ByteBuffer to write to
     */
    public JsonWriter(ByteBuffer buffer) {
        this.output = new ByteBufferOutputCursor(buffer);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes directly to a Vert.x Buffer.
     * 
     * @param buffer the Vert.x Buffer to write to
     */
    public JsonWriter(Buffer buffer) {
        this.output = new BufferOutputCursor(buffer);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Create a JsonWriter that writes directly to a Netty ByteBuf.
     * 
     * @param buffer the ByteBuf to write to
     */
    public JsonWriter(ByteBuf buffer) {
        this.output = new ByteBufOutputCursor(buffer);
        this.needsComma = false;
        this.depth = 0;
    }
    
    /**
     * Reset the writer for reuse.
     * Clears the internal buffer and state.
     */
    public void reset() {
        output.reset();
        needsComma = false;
        depth = 0;
    }
    
    /**
     * Reset the writer to use a different output cursor.
     * 
     * @param newOutput the new output cursor
     */
    public void reset(OutputCursor newOutput) {
        this.output = newOutput;
        needsComma = false;
        depth = 0;
    }
    
    /**
     * Reset the writer to write to an external byte array.
     * 
     * @param buffer the byte array to write to
     */
    public void reset(byte[] buffer) {
        if (output instanceof ByteArrayOutputCursor) {
            ((ByteArrayOutputCursor) output).reset(buffer);
        } else {
            this.output = new ByteArrayOutputCursor(buffer);
        }
        needsComma = false;
        depth = 0;
    }
    
    /**
     * Reset the writer to write to a ByteBuffer.
     * 
     * @param buffer the ByteBuffer to write to
     */
    public void reset(ByteBuffer buffer) {
        if (output instanceof ByteBufferOutputCursor) {
            ((ByteBufferOutputCursor) output).reset(buffer);
        } else {
            this.output = new ByteBufferOutputCursor(buffer);
        }
        needsComma = false;
        depth = 0;
    }
    
    /**
     * Reset the writer to write to a Vert.x Buffer.
     * 
     * @param buffer the Buffer to write to
     */
    public void reset(Buffer buffer) {
        if (output instanceof BufferOutputCursor) {
            ((BufferOutputCursor) output).reset(buffer);
        } else {
            this.output = new BufferOutputCursor(buffer);
        }
        needsComma = false;
        depth = 0;
    }
    
    /**
     * Reset the writer to write to a Netty ByteBuf.
     * 
     * @param buffer the ByteBuf to write to
     */
    public void reset(ByteBuf buffer) {
        if (output instanceof ByteBufOutputCursor) {
            ((ByteBufOutputCursor) output).reset(buffer);
        } else {
            this.output = new ByteBufOutputCursor(buffer);
        }
        needsComma = false;
        depth = 0;
    }
    
    // ========== Structure Methods ==========
    
    /**
     * Write the start of a JSON object.
     */
    public void objectStart() {
        writeCommaIfNeeded();
        output.writeByte(OBJECT_START);
        needsComma = false;
        depth++;
    }
    
    /**
     * Write the end of a JSON object.
     */
    public void objectEnd() {
        output.writeByte(OBJECT_END);
        needsComma = true;
        depth--;
    }
    
    /**
     * Write the start of a JSON array.
     */
    public void arrayStart() {
        writeCommaIfNeeded();
        output.writeByte(ARRAY_START);
        needsComma = false;
        depth++;
    }
    
    /**
     * Write the end of a JSON array.
     */
    public void arrayEnd() {
        output.writeByte(ARRAY_END);
        needsComma = true;
        depth--;
    }
    
    // ========== Field Name Methods ==========
    
    /**
     * Write a field name (String).
     * Must be followed by a value.
     * 
     * @param name the field name
     */
    public void fieldName(String name) {
        writeCommaIfNeeded();
        StringEscaper.writeString(name, output);
        output.writeByte(COLON);
        needsComma = false;
    }
    
    /**
     * Write a field name from a ByteSlice (zero-copy from parsed JSON).
     * Must be followed by a value.
     * 
     * @param name the field name as ByteSlice
     */
    public void fieldName(ByteSlice name) {
        writeCommaIfNeeded();
        StringEscaper.writeString(name, output);
        output.writeByte(COLON);
        needsComma = false;
    }
    
    /**
     * Write a field name from raw bytes (assumed already escaped).
     * Must be followed by a value.
     * 
     * @param name the field name bytes (without quotes)
     */
    public void fieldNameRaw(byte[] name) {
        writeCommaIfNeeded();
        output.writeByte(QUOTE);
        output.writeBytes(name, 0, name.length);
        output.writeByte(QUOTE);
        output.writeByte(COLON);
        needsComma = false;
    }
    
    // ========== Value Methods ==========
    
    /**
     * Write a string value.
     * 
     * @param value the string value
     */
    public void writeString(String value) {
        writeCommaIfNeeded();
        if (value == null) {
            output.writeBytes(NULL_BYTES, 0, NULL_BYTES.length);
        } else {
            StringEscaper.writeString(value, output);
        }
        needsComma = true;
    }
    
    /**
     * Write a string value from a ByteSlice.
     * 
     * @param value the string value as ByteSlice
     */
    public void writeString(ByteSlice value) {
        writeCommaIfNeeded();
        if (value == null) {
            output.writeBytes(NULL_BYTES, 0, NULL_BYTES.length);
        } else {
            StringEscaper.writeString(value, output);
        }
        needsComma = true;
    }
    
    /**
     * Write a raw string value (assumed already escaped, without quotes).
     * Adds quotes around the value.
     * 
     * @param value the raw string bytes
     */
    public void writeStringRaw(byte[] value) {
        writeCommaIfNeeded();
        output.writeByte(QUOTE);
        output.writeBytes(value, 0, value.length);
        output.writeByte(QUOTE);
        needsComma = true;
    }
    
    /**
     * Write an int value.
     * Uses instance scratch buffer - zero allocation when reusing JsonWriter.
     * 
     * @param value the int value
     */
    public void writeInt(int value) {
        writeCommaIfNeeded();
        int len = NumberSerializer.writeInt(value, numberScratch);
        output.writeBytes(numberScratch, 0, len);
        needsComma = true;
    }
    
    /**
     * Write a long value.
     * Uses instance scratch buffer - zero allocation when reusing JsonWriter.
     * 
     * @param value the long value
     */
    public void writeLong(long value) {
        writeCommaIfNeeded();
        int len = NumberSerializer.writeLong(value, numberScratch);
        output.writeBytes(numberScratch, 0, len);
        needsComma = true;
    }
    
    /**
     * Write a double value.
     * Uses instance scratch buffer - zero allocation when reusing JsonWriter.
     * 
     * @param value the double value
     */
    public void writeDouble(double value) {
        writeCommaIfNeeded();
        int len = NumberSerializer.writeDouble(value, numberScratch);
        output.writeBytes(numberScratch, 0, len);
        needsComma = true;
    }
    
    /**
     * Write a float value.
     * 
     * @param value the float value
     */
    public void writeFloat(float value) {
        writeDouble(value);
    }
    
    /**
     * Write a boolean value.
     * 
     * @param value the boolean value
     */
    public void writeBoolean(boolean value) {
        writeCommaIfNeeded();
        if (value) {
            output.writeBytes(TRUE_BYTES, 0, TRUE_BYTES.length);
        } else {
            output.writeBytes(FALSE_BYTES, 0, FALSE_BYTES.length);
        }
        needsComma = true;
    }
    
    /**
     * Write a null value.
     */
    public void writeNull() {
        writeCommaIfNeeded();
        output.writeBytes(NULL_BYTES, 0, NULL_BYTES.length);
        needsComma = true;
    }
    
    /**
     * Write raw bytes directly to the output.
     * Use for pre-serialized JSON content.
     * 
     * @param bytes the raw bytes to write
     */
    public void writeRaw(byte[] bytes) {
        writeCommaIfNeeded();
        output.writeBytes(bytes, 0, bytes.length);
        needsComma = true;
    }
    
    /**
     * Write raw bytes directly to the output.
     * 
     * @param bytes the raw bytes to write
     * @param offset the starting offset
     * @param length the number of bytes to write
     */
    public void writeRaw(byte[] bytes, int offset, int length) {
        writeCommaIfNeeded();
        output.writeBytes(bytes, offset, length);
        needsComma = true;
    }
    
    /**
     * Write raw bytes from a ByteSlice.
     * 
     * @param slice the ByteSlice containing raw JSON
     */
    public void writeRaw(ByteSlice slice) {
        writeCommaIfNeeded();
        output.writeBytes(slice);
        needsComma = true;
    }
    
    // ========== Convenience Field+Value Methods ==========
    
    /**
     * Write a string field (name + value).
     * 
     * @param name the field name
     * @param value the string value
     */
    public void field(String name, String value) {
        fieldName(name);
        writeString(value);
    }
    
    /**
     * Write an int field (name + value).
     * 
     * @param name the field name
     * @param value the int value
     */
    public void field(String name, int value) {
        fieldName(name);
        writeInt(value);
    }
    
    /**
     * Write a long field (name + value).
     * 
     * @param name the field name
     * @param value the long value
     */
    public void field(String name, long value) {
        fieldName(name);
        writeLong(value);
    }
    
    /**
     * Write a double field (name + value).
     * 
     * @param name the field name
     * @param value the double value
     */
    public void field(String name, double value) {
        fieldName(name);
        writeDouble(value);
    }
    
    /**
     * Write a boolean field (name + value).
     * 
     * @param name the field name
     * @param value the boolean value
     */
    public void field(String name, boolean value) {
        fieldName(name);
        writeBoolean(value);
    }
    
    /**
     * Write a null field (name + null value).
     * 
     * @param name the field name
     */
    public void fieldNull(String name) {
        fieldName(name);
        writeNull();
    }
    
    // ========== Output Methods ==========
    
    /**
     * Get the written bytes as a new byte array.
     * 
     * @return a new byte array containing the JSON
     */
    public byte[] toBytes() {
        return output.toBytes();
    }
    
    /**
     * Get the number of bytes written.
     * 
     * @return the number of bytes written
     */
    public int size() {
        return output.position();
    }
    
    /**
     * Get the underlying output cursor.
     * 
     * @return the output cursor
     */
    public OutputCursor getOutput() {
        return output;
    }
    
    /**
     * Get the current nesting depth.
     * 
     * @return the depth (0 = top level)
     */
    public int getDepth() {
        return depth;
    }
    
    // ========== Internal Methods ==========
    
    private void writeCommaIfNeeded() {
        if (needsComma) {
            output.writeByte(COMMA);
        }
    }
}

