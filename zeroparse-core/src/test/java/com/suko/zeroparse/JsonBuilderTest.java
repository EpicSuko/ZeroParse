package com.suko.zeroparse;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the fluent JsonBuilder API.
 */
public class JsonBuilderTest {
    
    @Test
    void testSimpleObject() {
        byte[] result = JsonBuilder.object()
            .field("name", "test")
            .end()
            .toBytes();
        
        assertEquals("{\"name\":\"test\"}", new String(result, StandardCharsets.UTF_8));
    }
    
    @Test
    void testObjectWithMultipleFields() {
        byte[] result = JsonBuilder.object()
            .field("symbol", "BTCUSDT")
            .field("price", 27000.50)
            .field("quantity", 100)
            .field("active", true)
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"symbol\":\"BTCUSDT\""));
        assertTrue(json.contains("\"price\":27000.5"));
        assertTrue(json.contains("\"quantity\":100"));
        assertTrue(json.contains("\"active\":true"));
    }
    
    @Test
    void testSimpleArray() {
        byte[] result = JsonBuilder.array()
            .value(1)
            .value(2)
            .value(3)
            .endArray()
            .toBytes();
        
        assertEquals("[1,2,3]", new String(result, StandardCharsets.UTF_8));
    }
    
    @Test
    void testArrayOfStrings() {
        byte[] result = JsonBuilder.array()
            .value("apple")
            .value("banana")
            .value("cherry")
            .endArray()
            .toBytes();
        
        assertEquals("[\"apple\",\"banana\",\"cherry\"]", 
            new String(result, StandardCharsets.UTF_8));
    }
    
    @Test
    void testNestedObject() {
        byte[] result = JsonBuilder.object()
            .field("outer", "value")
            .object("nested")
                .field("inner", "value")
            .end()
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"nested\":{\"inner\":\"value\"}"));
    }
    
    @Test
    void testNestedArray() {
        byte[] result = JsonBuilder.object()
            .field("name", "test")
            .array("items")
                .value(1)
                .value(2)
                .value(3)
            .endArray()
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"items\":[1,2,3]"));
    }
    
    @Test
    void testArrayOfObjects() {
        byte[] result = JsonBuilder.array()
            .nestedObject()
                .field("id", 1)
            .end()
            .nestedObject()
                .field("id", 2)
            .end()
            .endArray()
            .toBytes();
        
        assertEquals("[{\"id\":1},{\"id\":2}]", 
            new String(result, StandardCharsets.UTF_8));
    }
    
    @Test
    void testComplexStructure() {
        byte[] result = JsonBuilder.object()
            .field("symbol", "BTCUSDT")
            .field("timestamp", 1700000000000L)
            .array("bids")
                .nestedArray()
                    .value(27000.0)
                    .value(1.5)
                .endArray()
                .nestedArray()
                    .value(26999.0)
                    .value(2.0)
                .endArray()
            .endArray()
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"symbol\":\"BTCUSDT\""));
        assertTrue(json.contains("\"bids\":[["));
        assertTrue(json.contains("27000"));
        assertTrue(json.contains("26999"));
    }
    
    @Test
    void testNullField() {
        byte[] result = JsonBuilder.object()
            .field("name", "test")
            .fieldNull("optional")
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"optional\":null"));
    }
    
    @Test
    void testBuilderReuse() {
        JsonBuilder builder = new JsonBuilder();
        
        // First use
        builder.objectStart()
            .field("first", "value")
            .end();
        String first = new String(builder.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"first\":\"value\"}", first);
        
        // Reset and reuse
        builder.reset();
        builder.objectStart()
            .field("second", "value")
            .end();
        String second = new String(builder.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"second\":\"value\"}", second);
    }
    
    @Test
    void testIntoExternalBuffer() {
        byte[] buffer = new byte[256];
        
        JsonBuilder builder = JsonBuilder.into(buffer)
            .objectStart()
            .field("test", "value")
            .end();
        
        int size = builder.size();
        String result = new String(buffer, 0, size, StandardCharsets.UTF_8);
        assertEquals("{\"test\":\"value\"}", result);
    }
    
    @Test
    void testIntoByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        
        JsonBuilder builder = JsonBuilder.into(buffer)
            .objectStart()
            .field("test", "value")
            .end();
        
        // The builder writes to the buffer
        int size = builder.size();
        buffer.flip();
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        
        assertEquals("{\"test\":\"value\"}", new String(bytes, StandardCharsets.UTF_8));
    }
    
    @Test
    void testCryptoTradeMessage() {
        byte[] result = JsonBuilder.object()
            .field("e", "trade")
            .field("E", 1700000000000L)
            .field("s", "BTCUSDT")
            .field("t", 12345L)
            .field("p", 27000.50)
            .field("q", 1.5)
            .field("T", 1700000000000L)
            .field("m", true)
            .end()
            .toBytes();
        
        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"e\":\"trade\""));
        assertTrue(json.contains("\"p\":27000.5"));
    }
}

