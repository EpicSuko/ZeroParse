package com.suko.zeroparse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for zero-allocation number parsing from JsonStringView.
 * Focus on real-world trading API use cases.
 */
public class JsonStringViewNumberParsingTest {
    
    @Test
    public void testParseQuotedNumbers() {
        // Real-world example: Binance order book format
        String json = "{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]]}";
        
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        JsonArray asks = obj.get("asks").asArray();
        
        // First ask
        JsonArray firstAsk = asks.get(0).asArray();
        double price1 = firstAsk.get(0).asString().parseDouble();
        double quantity1 = firstAsk.get(1).asString().parseDouble();
        
        assertEquals(27000.5, price1, 0.000001);
        assertEquals(8.760, quantity1, 0.000001);
        
        // Second ask
        JsonArray secondAsk = asks.get(1).asArray();
        double price2 = secondAsk.get(0).asString().parseDouble();
        double quantity2 = secondAsk.get(1).asString().parseDouble();
        
        assertEquals(27001.0, price2, 0.000001);
        assertEquals(0.400, quantity2, 0.000001);
    }
    
    @Test
    public void testParsePooledContext() {
        // Test zero-allocation parsing in pooled context
        String json = "{\"price\":\"123.45\",\"volume\":\"67890\"}";
        
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject obj = ctx.parse(json).asObject();
            
            double price = obj.get("price").asString().parseDouble();
            long volume = obj.get("volume").asString().parseLong();
            
            assertEquals(123.45, price, 0.000001);
            assertEquals(67890L, volume);
        }
    }
    
    @Test
    public void testParseAllTypes() {
        String json = "{\"a\":\"123\",\"b\":\"456.789\",\"c\":\"-999\",\"d\":\"0.001\"}";
        
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        // parseInt
        int intVal = obj.get("a").asString().parseInt();
        assertEquals(123, intVal);
        
        // parseDouble
        double doubleVal = obj.get("b").asString().parseDouble();
        assertEquals(456.789, doubleVal, 0.000001);
        
        // parseLong (negative)
        long longVal = obj.get("c").asString().parseLong();
        assertEquals(-999L, longVal);
        
        // parseFloat (small)
        float floatVal = obj.get("d").asString().parseFloat();
        assertEquals(0.001f, floatVal, 0.000001f);
    }
    
    @Test
    public void testCryptoTradingVolumes() {
        // Test various crypto trading scenarios
        String json = "{\"btc\":\"0.00012345\",\"usd\":\"50000.25\",\"satoshi\":\"100000000\"}";
        
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        double btcAmount = obj.get("btc").asString().parseDouble();
        assertEquals(0.00012345, btcAmount, 0.0000000001);
        
        double usdPrice = obj.get("usd").asString().parseDouble();
        assertEquals(50000.25, usdPrice, 0.01);
        
        long satoshis = obj.get("satoshi").asString().parseLong();
        assertEquals(100000000L, satoshis);
    }
    
    @Test
    public void testInvalidQuotedNumbers() {
        String json = "{\"bad\":\"not-a-number\"}";

        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        assertThrows(NumberFormatException.class, () -> 
            obj.get("bad").asString().parseDouble());
    }
    
    @Test
    public void testScientificNotationInStrings() {
        String json = "{\"sci\":\"1.23e-10\"}";

        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        double val = obj.get("sci").asString().parseDouble();
        assertEquals(1.23e-10, val, 1e-15);
    }
    
    @Test
    public void testCompareStringViewAndNumberView() {
        // Verify that parsing from string gives same result as number
        String json = "{\"asString\":\"123.456\",\"asNumber\":123.456}";

        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        double fromString = obj.get("asString").asString().parseDouble();
        double fromNumber = obj.get("asNumber").asNumber().asDouble();
        
        assertEquals(fromNumber, fromString, 0.000001);
    }
    
    @Test
    public void testHighThroughputScenario() {
        // Simulate parsing many price updates
        String json = "{\"updates\":[" +
            "\"27000.1\",\"27000.2\",\"27000.3\",\"27000.4\",\"27000.5\"," +
            "\"27000.6\",\"27000.7\",\"27000.8\",\"27000.9\",\"27001.0\"" +
            "]}";
        
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject obj = ctx.parse(json).asObject();
            JsonArray updates = obj.get("updates").asArray();
            
            double sum = 0.0;
            for (int i = 0; i < updates.size(); i++) {
                double price = updates.get(i).asString().parseDouble();
                sum += price;
            }
            
            // Verify we parsed all correctly
            assertEquals(270005.5, sum, 0.01);
        }
    }
    
    @Test
    public void testBigDecimalParsing() {
        String json = "{\"precise\":\"123.456789012345678901234567890\"}";

        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        java.math.BigDecimal bd = obj.get("precise").asString().parseBigDecimal();
        assertEquals("123.456789012345678901234567890", bd.toString());
    }
    
    @Test
    public void testZeroValues() {
        String json = "{\"zero\":\"0\",\"zeroFloat\":\"0.0\",\"negZero\":\"-0\"}";

        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        assertEquals(0, obj.get("zero").asString().parseInt());
        assertEquals(0.0, obj.get("zeroFloat").asString().parseDouble(), 0.0);
        assertEquals(0, obj.get("negZero").asString().parseInt());
    }
}

