package com.suko.zeroparse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the zero-allocation JSON serializer.
 */
public class JsonWriterTest {
    
    private JsonWriter writer;
    
    @BeforeEach
    void setUp() {
        writer = new JsonWriter();
    }
    
    // ========== Basic Structure Tests ==========
    
    @Test
    void testEmptyObject() {
        writer.objectStart();
        writer.objectEnd();
        
        assertEquals("{}", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testEmptyArray() {
        writer.arrayStart();
        writer.arrayEnd();
        
        assertEquals("[]", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testSimpleObject() {
        writer.objectStart();
        writer.field("name", "test");
        writer.objectEnd();
        
        assertEquals("{\"name\":\"test\"}", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testObjectWithMultipleFields() {
        writer.objectStart();
        writer.field("symbol", "BTCUSDT");
        writer.field("price", 27000.5);
        writer.field("quantity", 1);
        writer.objectEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"symbol\":\"BTCUSDT\",\"price\":27000.5,\"quantity\":1}", result);
    }
    
    @Test
    void testSimpleArray() {
        writer.arrayStart();
        writer.writeInt(1);
        writer.writeInt(2);
        writer.writeInt(3);
        writer.arrayEnd();
        
        assertEquals("[1,2,3]", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testNestedObject() {
        writer.objectStart();
        writer.fieldName("outer");
        writer.objectStart();
        writer.field("inner", "value");
        writer.objectEnd();
        writer.objectEnd();
        
        assertEquals("{\"outer\":{\"inner\":\"value\"}}", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testNestedArray() {
        writer.arrayStart();
        writer.arrayStart();
        writer.writeInt(1);
        writer.writeInt(2);
        writer.arrayEnd();
        writer.arrayStart();
        writer.writeInt(3);
        writer.writeInt(4);
        writer.arrayEnd();
        writer.arrayEnd();
        
        assertEquals("[[1,2],[3,4]]", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testArrayOfObjects() {
        writer.arrayStart();
        writer.objectStart();
        writer.field("id", 1);
        writer.objectEnd();
        writer.objectStart();
        writer.field("id", 2);
        writer.objectEnd();
        writer.arrayEnd();
        
        assertEquals("[{\"id\":1},{\"id\":2}]", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    // ========== Primitive Type Tests ==========
    
    @Test
    void testIntValues() {
        writer.arrayStart();
        writer.writeInt(0);
        writer.writeInt(1);
        writer.writeInt(-1);
        writer.writeInt(Integer.MAX_VALUE);
        writer.writeInt(Integer.MIN_VALUE);
        writer.arrayEnd();
        
        assertEquals("[0,1,-1,2147483647,-2147483648]", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testLongValues() {
        writer.arrayStart();
        writer.writeLong(0L);
        writer.writeLong(Long.MAX_VALUE);
        writer.writeLong(Long.MIN_VALUE);
        writer.arrayEnd();
        
        assertEquals("[0,9223372036854775807,-9223372036854775808]", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testDoubleValues() {
        writer.arrayStart();
        writer.writeDouble(0.0);
        writer.writeDouble(1.5);
        writer.writeDouble(-1.5);
        writer.writeDouble(27000.50);
        writer.writeDouble(0.00001);
        writer.arrayEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.startsWith("[0.0,1.5,-1.5,27000.5,"));
    }
    
    @Test
    void testBooleanValues() {
        writer.arrayStart();
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.arrayEnd();
        
        assertEquals("[true,false]", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testNullValue() {
        writer.arrayStart();
        writer.writeNull();
        writer.arrayEnd();
        
        assertEquals("[null]", new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    // ========== String Escaping Tests ==========
    
    @Test
    void testStringWithQuotes() {
        writer.objectStart();
        writer.field("message", "He said \"Hello\"");
        writer.objectEnd();
        
        assertEquals("{\"message\":\"He said \\\"Hello\\\"\"}",
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testStringWithBackslash() {
        writer.objectStart();
        writer.field("path", "C:\\Users\\test");
        writer.objectEnd();
        
        assertEquals("{\"path\":\"C:\\\\Users\\\\test\"}",
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testStringWithNewline() {
        writer.objectStart();
        writer.field("text", "line1\nline2");
        writer.objectEnd();
        
        assertEquals("{\"text\":\"line1\\nline2\"}",
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testStringWithTab() {
        writer.objectStart();
        writer.field("text", "col1\tcol2");
        writer.objectEnd();
        
        assertEquals("{\"text\":\"col1\\tcol2\"}",
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testStringWithControlCharacter() {
        writer.objectStart();
        writer.field("text", "hello\u0000world");
        writer.objectEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\\u0000"));
    }
    
    @Test
    void testUnicodeString() {
        writer.objectStart();
        writer.field("emoji", "Hello 🌍!");
        writer.objectEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("🌍"));
    }
    
    // ========== Reset and Reuse Tests ==========
    
    @Test
    void testReset() {
        writer.objectStart();
        writer.field("first", "value");
        writer.objectEnd();
        
        String first = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"first\":\"value\"}", first);
        
        writer.reset();
        
        writer.objectStart();
        writer.field("second", "value");
        writer.objectEnd();
        
        String second = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"second\":\"value\"}", second);
    }
    
    // ========== External Buffer Tests ==========
    
    @Test
    void testExternalByteArray() {
        byte[] buffer = new byte[256];
        JsonWriter externalWriter = new JsonWriter(buffer);
        
        externalWriter.objectStart();
        externalWriter.field("test", "value");
        externalWriter.objectEnd();
        
        int size = externalWriter.size();
        String result = new String(buffer, 0, size, StandardCharsets.UTF_8);
        assertEquals("{\"test\":\"value\"}", result);
    }
    
    @Test
    void testExternalByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        JsonWriter bbWriter = new JsonWriter(buffer);
        
        bbWriter.objectStart();
        bbWriter.field("test", "value");
        bbWriter.objectEnd();
        
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8);
        assertEquals("{\"test\":\"value\"}", result);
    }
    
    // ========== ByteSlice Tests ==========
    
    @Test
    void testByteSliceFieldName() {
        byte[] nameBytes = "fieldName".getBytes(StandardCharsets.UTF_8);
        ByteSlice name = new ByteSlice(nameBytes, 0, nameBytes.length);
        
        writer.objectStart();
        writer.fieldName(name);
        writer.writeString("value");
        writer.objectEnd();
        
        assertEquals("{\"fieldName\":\"value\"}", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testByteSliceValue() {
        byte[] valueBytes = "testValue".getBytes(StandardCharsets.UTF_8);
        ByteSlice value = new ByteSlice(valueBytes, 0, valueBytes.length);
        
        writer.objectStart();
        writer.fieldName("key");
        writer.writeString(value);
        writer.objectEnd();
        
        assertEquals("{\"key\":\"testValue\"}", 
            new String(writer.toBytes(), StandardCharsets.UTF_8));
    }
    
    // ========== Crypto Trading Example Tests ==========
    
    @Test
    void testCryptoOrderMessage() {
        writer.objectStart();
        writer.field("symbol", "BTCUSDT");
        writer.field("side", "BUY");
        writer.field("type", "LIMIT");
        writer.field("price", 27000.50);
        writer.field("quantity", 1.5);
        writer.field("timestamp", 1700000000000L);
        writer.objectEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"symbol\":\"BTCUSDT\""));
        assertTrue(result.contains("\"price\":27000.5"));
        assertTrue(result.contains("\"timestamp\":1700000000000"));
    }
    
    @Test
    void testOrderBookSnapshot() {
        writer.objectStart();
        writer.field("symbol", "BTCUSDT");
        writer.fieldName("bids");
        writer.arrayStart();
        
        // Bid 1
        writer.arrayStart();
        writer.writeDouble(27000.0);
        writer.writeDouble(1.5);
        writer.arrayEnd();
        
        // Bid 2
        writer.arrayStart();
        writer.writeDouble(26999.0);
        writer.writeDouble(2.0);
        writer.arrayEnd();
        
        writer.arrayEnd();
        writer.objectEnd();
        
        String result = new String(writer.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"bids\":[["));
        assertTrue(result.contains("27000"));
        assertTrue(result.contains("26999"));
        assertTrue(result.contains("1.5"));
    }
}

