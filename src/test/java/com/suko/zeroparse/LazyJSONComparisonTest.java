package com.suko.zeroparse;

import me.doubledutch.lazyjson.LazyObject;
import me.doubledutch.lazyjson.LazyArray;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify LazyJSON integration and basic comparison.
 */
public class LazyJSONComparisonTest {
    
    @Test
    public void testLazyJSONBasicParsing() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true,\"scores\":[95,87,92,88,91]}";
        
        // Test LazyJSON parsing
        LazyObject obj = new LazyObject(json);
        assertNotNull(obj);
        assertEquals("John Doe", obj.getString("name"));
        assertEquals(30, obj.getInt("age"));
        assertEquals("New York", obj.getString("city"));
        assertTrue(obj.getBoolean("active"));
        
        LazyArray scores = obj.getJSONArray("scores");
        assertNotNull(scores);
        assertEquals(5, scores.length());
        assertEquals(95, scores.getInt(0));
        assertEquals(87, scores.getInt(1));
    }
    
    @Test
    public void testZeroParseVsLazyJSONComparison() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true,\"scores\":[95,87,92,88,91]}";
        Buffer buffer = Buffer.buffer(json);
        
        // Parse with ZeroParse
        JsonValue zeroParseRoot = ZeroParse.parse(buffer);
        JsonObject zeroParseObj = zeroParseRoot.asObject();
        
        // Parse with LazyJSON
        LazyObject lazyJsonObj = new LazyObject(json);
        
        // Compare results
        assertEquals(lazyJsonObj.getString("name"), zeroParseObj.get("name").toString());
        assertEquals(lazyJsonObj.getInt("age"), zeroParseObj.get("age").asNumber().asInt());
        assertEquals(lazyJsonObj.getString("city"), zeroParseObj.get("city").toString());
        assertEquals(lazyJsonObj.getBoolean("active"), zeroParseObj.get("active").asBoolean().booleanValue());
        
        // Compare arrays
        LazyArray lazyJsonScores = lazyJsonObj.getJSONArray("scores");
        JsonArray zeroParseScores = zeroParseObj.get("scores").asArray();
        
        assertEquals(lazyJsonScores.length(), zeroParseScores.size());
        for (int i = 0; i < lazyJsonScores.length(); i++) {
            assertEquals(lazyJsonScores.getInt(i), zeroParseScores.get(i).asNumber().asInt());
        }
    }
    
    @Test
    public void testLazyJSONArrayExtraction() {
        // Test LazyJSON's specialty - array object extraction
        String arrayJson = "[{\"id\":1,\"name\":\"test1\"},{\"id\":2,\"name\":\"test2\"},{\"id\":3,\"name\":\"test3\"}]";
        LazyArray array = new LazyArray(arrayJson);
        
        assertEquals(3, array.length());
        
        for (int i = 0; i < array.length(); i++) {
            LazyObject obj = array.getJSONObject(i);
            assertNotNull(obj);
            assertTrue(obj.getInt("id") > 0);
            assertTrue(obj.getString("name").startsWith("test"));
        }
    }
    
    @Test
    public void testPerformanceComparison() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true,\"scores\":[95,87,92,88,91]}";
        Buffer buffer = Buffer.buffer(json);
        
        int iterations = 10000;
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            ZeroParse.parse(buffer);
            new LazyObject(json);
        }
        
        // Benchmark ZeroParse
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            JsonValue root = ZeroParse.parse(buffer);
            JsonObject obj = root.asObject();
            obj.get("name").toString();
            obj.get("age").asNumber().asInt();
        }
        long zeroParseTime = System.nanoTime() - startTime;
        
        // Benchmark LazyJSON
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            LazyObject obj = new LazyObject(json);
            obj.getString("name");
            obj.getInt("age");
        }
        long lazyJsonTime = System.nanoTime() - startTime;
        
        System.out.println("ZeroParse time: " + (zeroParseTime / 1_000_000.0) + " ms");
        System.out.println("LazyJSON time: " + (lazyJsonTime / 1_000_000.0) + " ms");
        System.out.println("ZeroParse vs LazyJSON ratio: " + ((double) zeroParseTime / lazyJsonTime));
        
        // Both should complete successfully
        assertTrue(zeroParseTime > 0);
        assertTrue(lazyJsonTime > 0);
    }
}
