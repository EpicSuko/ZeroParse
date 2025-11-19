package com.suko.zeroparse.example;

import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;

/**
 * Examples demonstrating garbage-free parsing with JsonParseContext.
 * 
 * <p>This shows how to use the Arena-based allocation pattern for truly
 * garbage-free JSON parsing in high-throughput scenarios.</p>
 */
public class PooledParsingExample {
    
    public static void main(String[] args) {
        exampleBasicUsage();
        exampleNestedAccess();
        exampleWebSocketHandler();
    }
    
    /**
     * Basic usage with automatic cleanup.
     */
    private static void exampleBasicUsage() {
        System.out.println("=== Basic Pooled Parsing ===");
        
        String json = "{\"symbol\":\"BTCUSDT\",\"price\":50000.0,\"qty\":1.5}";
        
        // All view objects are pooled and automatically returned on close()
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject order = ctx.parse(json).asObject();
            String symbol = order.get("symbol").asString().toString();
            double price = order.get("price").asNumber().asDouble();
            double qty = order.get("qty").asNumber().asDouble();
            
            System.out.println("Symbol: " + symbol);
            System.out.println("Price: " + price);
            System.out.println("Quantity: " + qty);
        }  // <- All borrowed views automatically returned to pool!
        
        System.out.println();
    }
    
    /**
     * Nested object access with pooling.
     */
    private static void exampleNestedAccess() {
        System.out.println("=== Nested Object Pooling ===");
        
        String json = "{\"user\":{\"id\":123,\"name\":\"Alice\"},\"orders\":[{\"id\":1,\"total\":100.0},{\"id\":2,\"total\":200.0}]}";
        
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject root = ctx.parse(json).asObject();
            
            // Nested objects and arrays are also pooled!
            JsonObject user = root.getObject("user");
            String name = user.get("name").asString().toString();
            int userId = user.get("id").asNumber().asInt();
            
            System.out.println("User: " + name + " (ID: " + userId + ")");
            
            JsonArray orders = root.getArray("orders");
            System.out.println("Orders:");
            for (JsonValue orderValue : orders) {
                JsonObject order = orderValue.asObject();
                int id = order.get("id").asNumber().asInt();
                double total = order.get("total").asNumber().asDouble();
                System.out.println("  Order #" + id + ": $" + total);
            }
            
            // All views (root, user, orders, nested objects) returned to pool automatically!
        }
        
        System.out.println();
    }
    
    /**
     * Simulated WebSocket message handler (typical crypto trading use case).
     */
    private static void exampleWebSocketHandler() {
        System.out.println("=== WebSocket Handler Pattern ===");
        
        // Simulate receiving multiple messages
        String[] messages = {
            "{\"channel\":\"ticker\",\"data\":{\"symbol\":\"BTCUSDT\",\"price\":50000.0}}",
            "{\"channel\":\"ticker\",\"data\":{\"symbol\":\"ETHUSDT\",\"price\":3000.0}}",
            "{\"channel\":\"ticker\",\"data\":{\"symbol\":\"SOLUSDT\",\"price\":100.0}}"
        };
        
        for (String message : messages) {
            onWebSocketMessage(message);
        }
        
        System.out.println("All messages processed with ZERO view allocations (all pooled!)");
        System.out.println();
    }
    
    /**
     * WebSocket message handler using JsonParseContext for zero-allocation parsing.
     */
    private static void onWebSocketMessage(String message) {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject msg = ctx.parse(message).asObject();
            
            String channel = msg.get("channel").asString().toString();
            JsonObject data = msg.getObject("data");
            
            if ("ticker".equals(channel)) {
                String symbol = data.get("symbol").asString().toString();
                double price = data.get("price").asNumber().asDouble();
                System.out.println("Ticker Update: " + symbol + " @ $" + price);
            }
            
            // All views returned to pool automatically on close()
        }
    }
    
    /**
     * Performance comparison: regular parsing vs pooled parsing.
     */
    public static void performanceTest() {
        String json = "{\"symbol\":\"BTCUSDT\",\"price\":50000.0,\"qty\":1.5}";
        int iterations = 1_000_000;
        
        System.out.println("=== Performance Test (" + iterations + " iterations) ===");
        
        // Test 1: Regular parsing (allocates new views each time)
        long start = System.nanoTime();
        JsonParser parser = new JsonParser();
        for (int i = 0; i < iterations; i++) {
            JsonObject obj = parser.parse(json).asObject();
            obj.get("symbol").asString().toString();
            obj.get("price").asNumber().asDouble();
        }
        long regularTime = System.nanoTime() - start;
        System.out.println("Regular parsing: " + (regularTime / 1_000_000) + " ms");
        
        // Test 2: Pooled parsing (reuses views from pool)
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonObject obj = ctx.parse(json).asObject();
                obj.get("symbol").asString().toString();
                obj.get("price").asNumber().asDouble();
            }
        }
        long pooledTime = System.nanoTime() - start;
        System.out.println("Pooled parsing: " + (pooledTime / 1_000_000) + " ms");
        
        double improvement = ((double) (regularTime - pooledTime) / regularTime) * 100;
        System.out.println("Improvement: " + String.format("%.2f", improvement) + "%");
    }
}

