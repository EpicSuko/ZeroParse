package com.suko.zeroparse.example;

import com.jprofiler.api.controller.Controller;
import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;

/**
 * JProfiler profiling example for ZeroParse.
 * 
 * <p>This example provides multiple scenarios to profile different aspects
 * of the parser under realistic trading system conditions.</p>
 */
public class ZeroParseExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ZeroParse JProfiler Profiling ===\n");
        
        // Warm up JIT compiler (important for accurate profiling)
        System.out.println("Phase 1: Warming up JIT compiler...");
        warmUp();
        System.out.println("Warm-up complete. JIT should be optimized.\n");
        
        // Wait for user to start JProfiler CPU recording
        System.out.println("Start JProfiler CPU recording now, then press Enter...");
        System.in.read();
        
        // Profile different scenarios
        System.out.println("\n=== Profiling Scenario 1: Order Book Snapshot (typical crypto message) ===");
        profileOrderBookSnapshot();
        
        System.out.println("\n=== Profiling Scenario 2: Trade Stream (many small messages) ===");
        profileTradeStream();
        
        System.out.println("\n=== Profiling Scenario 3: Large Array Processing ===");
        profileLargeArray();
        
        System.out.println("\n=== Profiling Scenario 4: Nested Objects (complex structure) ===");
        profileNestedObjects();
        
        System.out.println("\n=== Profiling Complete ===");
        System.out.println("Stop JProfiler CPU recording and analyze results.");
    }
    
    private static void warmUp() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        // Run 50k iterations to warm up JIT
        for (int i = 0; i < 50_000; i++) {
            JsonValue root = parser.parse(buffer);
            // Access some fields to ensure they're not optimized away
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                obj.get("action");
                obj.get("ts");
            }
        }
    }
    
    /**
     * Profile typical order book snapshot parsing.
     * This is the most common message type in crypto trading.
     */
    private static void profileOrderBookSnapshot() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"],[\"27001.5\",\"5.200\"],[\"27002.0\",\"3.100\"],[\"27002.5\",\"1.500\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"],[\"26999.0\",\"4.320\"],[\"26998.5\",\"2.180\"],[\"26998.0\",\"6.540\"]],\"checksum\":12345,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startCPURecording(true);
        long startTime = System.nanoTime();
        
        // Simulate 1 second of trading at 100k messages/sec
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            
            // Typical access pattern in trading system
            JsonObject obj = root.asObject();
            String action = obj.get("action").toString();
            JsonArray data = obj.getArray("data");
            
            if (data != null && data.size() > 0) {
                JsonObject tick = data.get(0).asObject();
                JsonArray asks = tick.getArray("asks");
                JsonArray bids = tick.getArray("bids");
                
                // Access best bid/ask (most common operation)
                if (asks != null && asks.size() > 0) {
                    JsonArray bestAsk = asks.get(0).asArray();
                    String price = bestAsk.get(0).toString();
                    String qty = bestAsk.get(1).toString();
                }
                
                if (bids != null && bids.size() > 0) {
                    JsonArray bestBid = bids.get(0).asArray();
                    String price = bestBid.get(0).toString();
                    String qty = bestBid.get(1).toString();
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopCPURecording();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = 100_000 / (durationMs / 1000.0);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f messages/sec\n", throughput);
    }
    
    /**
     * Profile many small trade messages.
     * Tests parser efficiency with simpler structures.
     */
    private static void profileTradeStream() {
        String json = "{\"stream\":\"btcusdt@trade\",\"data\":{\"e\":\"trade\",\"E\":1695716059516,\"s\":\"BTCUSDT\",\"t\":123456789,\"p\":\"27000.50\",\"q\":\"0.125\",\"b\":987654321,\"a\":123456789,\"T\":1695716059515,\"m\":true}}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startCPURecording(true);
        long startTime = System.nanoTime();
        
        // Simulate high-frequency trade stream
        for (int i = 0; i < 200_000; i++) {
            JsonValue root = parser.parse(buffer);
            
            // Typical trade processing
            JsonObject obj = root.asObject();
            String stream = obj.get("stream").toString();
            JsonObject data = obj.getObject("data");
            
            if (data != null) {
                String price = data.get("p").toString();
                String quantity = data.get("q").toString();
                boolean isBuyerMaker = data.getBoolean("m").booleanValue();
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopCPURecording();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = 200_000 / (durationMs / 1000.0);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f messages/sec\n", throughput);
    }
    
    /**
     * Profile large array parsing.
     * Tests array iteration and element access performance.
     */
    private static void profileLargeArray() {
        // Create array with 50 price levels
        StringBuilder sb = new StringBuilder();
        sb.append("{\"channel\":\"depth\",\"levels\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb.append(",");
            sb.append("[\"").append(27000 + i).append("\",\"").append(1.5 + i * 0.1).append("\"]");
        }
        sb.append("]}");
        
        String json = sb.toString();
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startCPURecording(true);
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 50_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            JsonArray levels = obj.getArray("levels");
            
            // Process all levels
            if (levels != null) {
                for (int j = 0; j < levels.size(); j++) {
                    JsonArray level = levels.get(j).asArray();
                    String price = level.get(0).toString();
                    String qty = level.get(1).toString();
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopCPURecording();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = 50_000 / (durationMs / 1000.0);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f messages/sec\n", throughput);
    }
    
    /**
     * Profile deeply nested object structures.
     * Tests recursive parsing performance.
     */
    private static void profileNestedObjects() {
        String json = "{\"level1\":{\"level2\":{\"level3\":{\"level4\":{\"level5\":{\"price\":\"27000.50\",\"quantity\":\"1.234\",\"timestamp\":1695716059516,\"metadata\":{\"exchange\":\"binance\",\"symbol\":\"BTCUSDT\",\"type\":\"limit\"}}}}}}}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startCPURecording(true);
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            
            // Navigate through nested structure
            JsonObject obj = root.asObject();
            JsonObject l1 = obj.getObject("level1");
            if (l1 != null) {
                JsonObject l2 = l1.getObject("level2");
                if (l2 != null) {
                    JsonObject l3 = l2.getObject("level3");
                    if (l3 != null) {
                        JsonObject l4 = l3.getObject("level4");
                        if (l4 != null) {
                            JsonObject l5 = l4.getObject("level5");
                            if (l5 != null) {
                                String price = l5.get("price").toString();
                                JsonObject metadata = l5.getObject("metadata");
                                if (metadata != null) {
                                    String exchange = metadata.get("exchange").toString();
                                }
                            }
                        }
                    }
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopCPURecording();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = 100_000 / (durationMs / 1000.0);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f messages/sec\n", throughput);
    }
}
