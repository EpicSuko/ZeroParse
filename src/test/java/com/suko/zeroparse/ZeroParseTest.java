package com.suko.zeroparse;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for ZeroParse functionality.
 */
public class ZeroParseTest {
    
    @Test
    public void testParseSimpleObject() {
        String json = "{\"name\":\"test\",\"value\":42}";
        Buffer buffer = Buffer.buffer(json);
        
        JsonValue root = new JsonParser().parse(buffer);
        assertNotNull(root);
        assertTrue(root.isObject());
        
        JsonObject obj = root.asObject();
        assertEquals(2, obj.size());
        
        JsonValue name = obj.get("name");
        assertNotNull(name);
        assertEquals("test", name.toString());
        
        JsonNumberView value = obj.get("value").asNumber();
        assertNotNull(value);
        assertEquals(42, value.asInt());
    }
    
    @Test
    public void testParseSimpleArray() {
        String json = "[1,2,3]";
        Buffer buffer = Buffer.buffer(json);
        
        JsonValue root = new JsonParser().parse(buffer);
        assertNotNull(root);
        assertTrue(root.isArray());
        
        JsonArray array = root.asArray();
        assertEquals(3, array.size());
        
        assertEquals(1, array.get(0).asNumber().asInt());
        assertEquals(2, array.get(1).asNumber().asInt());
        assertEquals(3, array.get(2).asNumber().asInt());
    }
    
    @Test
    public void testParseString() {
        String json = "\"hello world\"";
        Buffer buffer = Buffer.buffer(json);
        
        JsonValue root = new JsonParser().parse(buffer);
        assertNotNull(root);
        assertTrue(root.isString());
        
        assertEquals("hello world", root.toString());
    }
    
    @Test
    public void testParseBoolean() {
        Buffer trueBuffer = Buffer.buffer("true");
        JsonValue trueValue = new JsonParser().parse(trueBuffer);
        assertTrue(trueValue.isBoolean());
        assertEquals(JsonBoolean.TRUE, trueValue.asBoolean());
        
        Buffer falseBuffer = Buffer.buffer("false");
        JsonValue falseValue = new JsonParser().parse(falseBuffer);
        assertTrue(falseValue.isBoolean());
        assertEquals(JsonBoolean.FALSE, falseValue.asBoolean());
    }
    
    @Test
    public void testParseNull() {
        Buffer buffer = Buffer.buffer("null");
        JsonValue root = new JsonParser().parse(buffer);
        assertNotNull(root);
        assertTrue(root.isNull());
        assertEquals(JsonNull.INSTANCE, root);
    }
    
    @Test
    public void testParseNumber() {
        Buffer buffer = Buffer.buffer("123.45");
        JsonValue root = new JsonParser().parse(buffer);
        assertNotNull(root);
        assertTrue(root.isNumber());
        
        JsonNumberView num = root.asNumber();
        assertEquals(123.45, num.asDouble(), 0.001);
    }
    
    @Test
    public void testParseFromString() {
        String json = "{\"message\":\"Hello, World!\"}";
        JsonValue root = new JsonParser().parse(json);
        assertNotNull(root);
        assertTrue(root.isObject());
        
        JsonObject obj = root.asObject();
        JsonValue message = obj.get("message");
        assertEquals("Hello, World!", message.toString());
    }
    
    @Test
    public void testParseFromByteArray() {
        String json = "{\"id\":123}";
        byte[] data = json.getBytes();
        JsonValue root = new JsonParser().parse(data, 0, data.length);
        assertNotNull(root);
        assertTrue(root.isObject());
        
        JsonObject obj = root.asObject();
        JsonNumberView id = obj.get("id").asNumber();
        assertEquals(123, id.asInt());
    }
    
    @Test
    public void testStreamArray() {
        String json = "[{\"x\":1},{\"y\":2},{\"z\":3}]";
        Buffer buffer = Buffer.buffer(json);
        
        JsonArrayCursor cursor = new JsonParser().streamArray(buffer);
        assertNotNull(cursor);
        assertEquals(3, cursor.size());
        
        int index = 0;
        while (cursor.hasNext()) {
            JsonValue element = cursor.next();
            assertTrue(element.isObject());
            JsonObject obj = element.asObject();
            assertTrue(obj.size() == 1);
            index++;
        }
        assertEquals(3, index);
    }
}
