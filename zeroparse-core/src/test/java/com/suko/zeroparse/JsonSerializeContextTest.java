package com.suko.zeroparse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JsonSerializeContext.
 */
public class JsonSerializeContextTest {
    
    private JsonSerializeContext ctx;
    
    @BeforeEach
    void setUp() {
        ctx = new JsonSerializeContext();
    }
    
    @Test
    void testBasicUsage() {
        ctx.reset();
        JsonWriter writer = ctx.writer();
        
        writer.objectStart();
        writer.field("test", "value");
        writer.objectEnd();
        
        assertEquals("{\"test\":\"value\"}", 
            new String(ctx.toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testContextReuse() {
        // First serialization
        ctx.reset();
        ctx.writer().objectStart();
        ctx.writer().field("first", 1);
        ctx.writer().objectEnd();
        
        String first = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"first\":1}", first);
        
        // Second serialization
        ctx.reset();
        ctx.writer().objectStart();
        ctx.writer().field("second", 2);
        ctx.writer().objectEnd();
        
        String second = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        assertEquals("{\"second\":2}", second);
    }
    
    @Test
    void testConvenienceMethods() {
        ctx.reset();
        ctx.objectStart()
           .field("name", "test")
           .field("value", 42)
           .objectEnd();
        
        String result = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"name\":\"test\""));
        assertTrue(result.contains("\"value\":42"));
    }
    
    @Test
    void testExternalByteArray() {
        byte[] buffer = new byte[256];
        
        ctx.reset(buffer);
        ctx.objectStart()
           .field("external", true)
           .objectEnd();
        
        int size = ctx.size();
        String result = new String(buffer, 0, size, StandardCharsets.UTF_8);
        assertEquals("{\"external\":true}", result);
    }
    
    @Test
    void testExternalByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        
        ctx.reset(buffer);
        ctx.objectStart()
           .field("bytebuffer", "test")
           .objectEnd();
        
        // Verify data was written to the buffer
        int size = ctx.size();
        buffer.flip();
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        
        assertEquals("{\"bytebuffer\":\"test\"}", new String(bytes, StandardCharsets.UTF_8));
    }
    
    @Test
    void testMultipleExternalBufferTypes() {
        // Start with internal buffer
        ctx.reset();
        ctx.objectStart().field("internal", true).objectEnd();
        String internal = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        
        // Switch to external byte array
        byte[] byteArray = new byte[256];
        ctx.reset(byteArray);
        ctx.objectStart().field("array", true).objectEnd();
        
        // Switch to ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        ctx.reset(byteBuffer);
        ctx.objectStart().field("buffer", true).objectEnd();
        
        // Switch back to internal
        ctx.reset();
        ctx.objectStart().field("back", true).objectEnd();
        String back = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        
        assertEquals("{\"internal\":true}", internal);
        assertEquals("{\"back\":true}", back);
    }
    
    @Test
    void testCopyTo() {
        ctx.reset();
        ctx.objectStart()
           .field("test", "value")
           .objectEnd();
        
        byte[] dest = new byte[256];
        int copied = ctx.copyTo(dest, 0);
        
        assertEquals(ctx.size(), copied);
        assertEquals("{\"test\":\"value\"}", new String(dest, 0, copied, StandardCharsets.UTF_8));
    }
    
    @Test
    void testCopyToWithOffset() {
        ctx.reset();
        ctx.objectStart()
           .field("test", "value")
           .objectEnd();
        
        byte[] dest = new byte[256];
        dest[0] = 'X';
        dest[1] = 'X';
        
        int copied = ctx.copyTo(dest, 2);
        
        assertTrue(copied > 0);
        assertEquals('X', dest[0]);
        assertEquals('X', dest[1]);
        assertEquals('{', dest[2]);
    }
    
    @Test
    void testSize() {
        ctx.reset();
        assertEquals(0, ctx.size());
        
        ctx.objectStart();
        assertTrue(ctx.size() > 0);
        
        ctx.field("test", "value");
        int sizeWithField = ctx.size();
        
        ctx.objectEnd();
        assertTrue(ctx.size() > sizeWithField);
    }
    
    @Test
    void testLargeDocument() {
        ctx.reset();
        ctx.objectStart();
        
        for (int i = 0; i < 1000; i++) {
            ctx.field("field" + i, i);
        }
        
        ctx.objectEnd();
        
        byte[] result = ctx.toBytes();
        String json = new String(result, StandardCharsets.UTF_8);
        
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"field0\":0"));
        assertTrue(json.contains("\"field999\":999"));
    }
    
    @Test
    void testBuilderAccess() {
        ctx.reset();
        JsonBuilder builder = ctx.builder();
        
        builder.objectStart()
               .field("via", "builder")
               .end();
        
        // Should be able to get the result from context
        assertTrue(ctx.size() > 0);
        String result = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"via\":\"builder\""));
    }
    
    @Test
    void testCryptoMessageSerialization() {
        ctx.reset();
        
        // Simulate serializing a WebSocket message
        JsonWriter writer = ctx.writer();
        writer.objectStart();
        writer.field("method", "SUBSCRIBE");
        writer.fieldName("params");
        writer.arrayStart();
        writer.writeString("btcusdt@trade");
        writer.writeString("ethusdt@trade");
        writer.arrayEnd();
        writer.field("id", 1);
        writer.objectEnd();
        
        String result = new String(ctx.toBytes(), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"method\":\"SUBSCRIBE\""));
        assertTrue(result.contains("\"params\":[\"btcusdt@trade\",\"ethusdt@trade\"]"));
        assertTrue(result.contains("\"id\":1"));
    }
}

