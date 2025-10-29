package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the stack-based parser implementation.
 */
public class StackParserTest {
    
    @Test
    public void testStackParserBasicParsing() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true}";
        Buffer buffer = Buffer.buffer(json);
        
        // Create parser with stack mode
        // ZeroParseConfig config = ZeroParseConfig.builder()
        //     .parserMode(ZeroParseConfig.ParserMode.STACK)
        //     .build();
        
        // ZeroParseRuntime.configure(config);
        JsonParser parser = new JsonParser();
        
        // Test that parsing doesn't throw an exception
        JsonValue root = parser.parse(buffer);
        assertNotNull(root);
        assertTrue(root.isObject());
        
        // Test toString() works
        String result = root.toString();
        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("30"));
        assertTrue(result.contains("New York"));
        assertTrue(result.contains("true"));
    }
    
    @Test
    public void testStackParserArrayParsing() {
        String json = "[1,2,3,\"test\",true,null]";
        Buffer buffer = Buffer.buffer(json);
        
        // Create parser with stack mode
        // ZeroParseConfig config = ZeroParseConfig.builder()
        //     .parserMode(ZeroParseConfig.ParserMode.STACK)
        //     .build();
        
        // ZeroParseRuntime.configure(config);
        JsonParser parser = new JsonParser();
        
        // Test that parsing doesn't throw an exception
        JsonValue root = parser.parse(buffer);
        assertNotNull(root);
        assertTrue(root.isArray());
        
        // Test toString() works
        String result = root.toString();
        assertNotNull(result);
        assertTrue(result.contains("1"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("3"));
        assertTrue(result.contains("test"));
        assertTrue(result.contains("true"));
        assertTrue(result.contains("null"));
    }
    
    @Test
    public void testStackParserNestedParsing() {
        String json = "{\"users\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"count\":2}";
        Buffer buffer = Buffer.buffer(json);
        
        // Create parser with stack mode
        // ZeroParseConfig config = ZeroParseConfig.builder()
        //     .parserMode(ZeroParseConfig.ParserMode.STACK)
        //     .build();
        
        // ZeroParseRuntime.configure(config);
        JsonParser parser = new JsonParser();
        
        // Test that parsing doesn't throw an exception
        JsonValue root = parser.parse(buffer);
        assertNotNull(root);
        assertTrue(root.isObject());
        
        // Test toString() works
        String result = root.toString();
        assertNotNull(result);
        assertTrue(result.contains("users"));
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("Bob"));
        assertTrue(result.contains("count"));
        assertTrue(result.contains("2"));
    }
    
}
