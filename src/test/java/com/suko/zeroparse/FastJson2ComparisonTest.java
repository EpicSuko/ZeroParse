package com.suko.zeroparse;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify FastJson2 integration and basic comparison.
 */
public class FastJson2ComparisonTest {
    
    @Test
    public void testFastJson2BasicParsing() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true,\"scores\":[95,87,92,88,91]}";
        
        // Test FastJson2 parsing
        JSONObject obj = JSON.parseObject(json);
        assertNotNull(obj);
        assertEquals("John Doe", obj.getString("name"));
        assertEquals(30, obj.getIntValue("age"));
        assertEquals("New York", obj.getString("city"));
        assertTrue(obj.getBooleanValue("active"));
        
        JSONArray scores = obj.getJSONArray("scores");
        assertNotNull(scores);
        assertEquals(5, scores.size());
        assertEquals(95, scores.getIntValue(0));
        assertEquals(87, scores.getIntValue(1));
    }
    
    @Test
    public void testZeroParseVsFastJson2Comparison() {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"active\":true,\"scores\":[95,87,92,88,91]}";
        Buffer buffer = Buffer.buffer(json);
        
        // Parse with ZeroParse
        JsonValue zeroParseRoot = ZeroParse.parse(buffer);
        JsonObject zeroParseObj = zeroParseRoot.asObject();
        
        // Parse with FastJson2
        JSONObject fastJson2Obj = JSON.parseObject(json);
        
        // Compare results
        assertEquals(fastJson2Obj.getString("name"), zeroParseObj.get("name").toString());
        assertEquals(fastJson2Obj.getIntValue("age"), zeroParseObj.get("age").asNumber().asInt());
        assertEquals(fastJson2Obj.getString("city"), zeroParseObj.get("city").toString());
        assertEquals(fastJson2Obj.getBooleanValue("active"), zeroParseObj.get("active").asBoolean().booleanValue());
        
        // Compare arrays
        JSONArray fastJson2Scores = fastJson2Obj.getJSONArray("scores");
        JsonArray zeroParseScores = zeroParseObj.get("scores").asArray();
        
        assertEquals(fastJson2Scores.size(), zeroParseScores.size());
        for (int i = 0; i < fastJson2Scores.size(); i++) {
            assertEquals(fastJson2Scores.getIntValue(i), zeroParseScores.get(i).asNumber().asInt());
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
            JSON.parseObject(json);
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
        
        // Benchmark FastJson2
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            JSONObject obj = JSON.parseObject(json);
            obj.getString("name");
            obj.getIntValue("age");
        }
        long fastJson2Time = System.nanoTime() - startTime;
        
        System.out.println("ZeroParse time: " + (zeroParseTime / 1_000_000.0) + " ms");
        System.out.println("FastJson2 time: " + (fastJson2Time / 1_000_000.0) + " ms");
        System.out.println("ZeroParse vs FastJson2 ratio: " + ((double) zeroParseTime / fastJson2Time));
        
        // Both should complete successfully
        assertTrue(zeroParseTime > 0);
        assertTrue(fastJson2Time > 0);
    }
}
