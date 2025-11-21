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
    
    @Test
    public void testJsonStringViewZeroAllocationEquals() {
        String json = "{\"symbol\":\"BTCUSDT\",\"exchange\":\"Binance\",\"price\":\"27000.50\"}";
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        // Test equals with exact match
        JsonStringView symbol = obj.get("symbol").asString();
        byte[] btcusdt = "BTCUSDT".getBytes();
        assertTrue(symbol.equals(btcusdt));
        assertTrue(symbol.equals(btcusdt, 0, btcusdt.length));
        
        // Test equals with different content
        byte[] ethusdt = "ETHUSDT".getBytes();
        assertFalse(symbol.equals(ethusdt));
        
        // Test equals with different length
        byte[] btc = "BTC".getBytes();
        assertFalse(symbol.equals(btc));
        
        // Test equals with null
        assertFalse(symbol.equals((byte[]) null));
        assertFalse(symbol.equals(null, 0, 0));
        
        // Test equals with partial array
        byte[] largeBuf = "xxxBTCUSDTxxx".getBytes();
        assertTrue(symbol.equals(largeBuf, 3, 7)); // "BTCUSDT" at offset 3
        assertFalse(symbol.equals(largeBuf, 0, 7)); // "xxxBTCU"
        
        // Test with numeric string
        JsonStringView price = obj.get("price").asString();
        byte[] priceBytes = "27000.50".getBytes();
        assertTrue(price.equals(priceBytes));
        
        // Verify zero allocation by running in a loop
        // In a real benchmark this would be measured with JMH
        for (int i = 0; i < 1000; i++) {
            assertTrue(symbol.equals(btcusdt));
            assertTrue(price.equals(priceBytes));
        }
    }
    
    @Test
    public void testJsonStringViewAppendMethods() throws Exception {
        String json = "{\"simple\":\"Hello World\",\"escaped\":\"Line 1\\nLine 2\\tTabbed\",\"unicode\":\"Hello \\u4E16\\u754C\"}";
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        // Test getValue() method
        JsonStringView simple = obj.get("simple").asString();
        assertEquals("Hello World", simple.getValue());
        
        // Test appendTo(Appendable)
        StringBuilder sb = new StringBuilder();
        JsonStringView escaped = obj.get("escaped").asString();
        escaped.appendTo(sb);
        assertEquals("Line 1\nLine 2\tTabbed", sb.toString());
        
        // Test appendTo(Appendable) with chaining
        sb.setLength(0); // Clear
        simple.appendTo(sb).append(" - ").append("Added text");
        assertEquals("Hello World - Added text", sb.toString());
        
        // Test appendTo(Buffer)
        Buffer buffer = Buffer.buffer();
        simple.appendTo(buffer);
        assertEquals("Hello World", buffer.toString());
        
        // Test appendTo(Buffer) with escaped string
        buffer = Buffer.buffer();
        escaped.appendTo(buffer);
        assertEquals("Line 1\nLine 2\tTabbed", buffer.toString());
        
        // Test appendTo(Buffer) with unicode
        buffer = Buffer.buffer();
        JsonStringView unicode = obj.get("unicode").asString();
        unicode.appendTo(buffer);
        assertEquals("Hello 世界", buffer.toString("UTF-8"));
        
        // Test chaining with Buffer
        buffer = Buffer.buffer();
        buffer.appendString("Start: ");
        simple.appendTo(buffer).appendString(" :End");
        assertEquals("Start: Hello World :End", buffer.toString());
        
        // Test null safety
        assertThrows(NullPointerException.class, () -> simple.appendTo((Appendable) null));
        assertThrows(NullPointerException.class, () -> simple.appendTo((Buffer) null));
    }
}
