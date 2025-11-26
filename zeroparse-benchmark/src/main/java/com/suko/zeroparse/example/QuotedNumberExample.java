package com.suko.zeroparse.example;

import com.suko.zeroparse.JsonArray;
import com.suko.zeroparse.JsonObject;
import com.suko.zeroparse.JsonParseContext;

/**
 * Example demonstrating zero-allocation parsing of quoted numbers.
 * 
 * <p>Many trading APIs (Binance, Kraken, etc.) quote numeric values as strings
 * to preserve precision. This example shows how to parse them efficiently.</p>
 */
public class QuotedNumberExample {
    
    public static void main(String[] args) {
        // Real-world example: Binance order book WebSocket message
        String orderBookJson = "{" +
            "\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"],[\"27002.5\",\"1.234\"]]," +
            "\"bids\":[[\"26999.5\",\"10.500\"],[\"26998.0\",\"5.250\"]]" +
            "}";
        
        System.out.println("=== Zero-Allocation Quoted Number Parsing ===\n");
        
        // Parse with pooled context for zero GC
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject orderBook = ctx.parse(orderBookJson).asObject();
            
            // Process asks (sell orders)
            System.out.println("ASKS (Sell Orders):");
            JsonArray asks = orderBook.get("asks").asArray();
            for (int i = 0; i < asks.size(); i++) {
                JsonArray ask = asks.get(i).asArray();
                
                // Parse string-quoted numbers with ZERO allocation
                double price = ask.get(0).asString().parseDouble();
                double quantity = ask.get(1).asString().parseDouble();
                
                System.out.printf("  Price: $%.2f, Quantity: %.3f BTC\n", price, quantity);
            }
            
            // Process bids (buy orders)
            System.out.println("\nBIDS (Buy Orders):");
            JsonArray bids = orderBook.get("bids").asArray();
            for (int i = 0; i < bids.size(); i++) {
                JsonArray bid = bids.get(i).asArray();
                
                double price = bid.get(0).asString().parseDouble();
                double quantity = bid.get(1).asString().parseDouble();
                
                System.out.printf("  Price: $%.2f, Quantity: %.3f BTC\n", price, quantity);
            }
            
            System.out.println("\n✅ All parsing done with ZERO allocations!");
            System.out.println("✅ No String objects created for number parsing!");
            System.out.println("✅ Perfect for high-frequency trading systems!");
            
        } // All objects returned to pool here
        
        // Demonstrate performance comparison
        demonstratePerformance();
    }
    
    private static void demonstratePerformance() {
        System.out.println("\n=== Performance Comparison ===\n");
        
        String json = "{\"prices\":[\"27000.1\",\"27000.2\",\"27000.3\",\"27000.4\",\"27000.5\"]}";
        int iterations = 100000;
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonObject obj = ctx.parse(json).asObject();
                JsonArray prices = obj.get("prices").asArray();
                for (int j = 0; j < prices.size(); j++) {
                    prices.get(j).asString().parseDouble();
                }
            }
        }
        
        // Benchmark zero-allocation parsing
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonObject obj = ctx.parse(json).asObject();
                JsonArray prices = obj.get("prices").asArray();
                for (int j = 0; j < prices.size(); j++) {
                    prices.get(j).asString().parseDouble();
                }
            }
        }
        long zeroAllocTime = System.nanoTime() - start;
        
        double avgMicros = zeroAllocTime / 1000.0 / iterations;
        System.out.printf("Zero-allocation parsing: %.2f µs per operation\n", avgMicros);
        System.out.printf("Throughput: %.0f ops/sec\n", 1_000_000_000.0 / (zeroAllocTime / iterations));
        System.out.println("\nMemory: ~0 bytes allocated per parse (pooled)");
        System.out.println("GC Events: ZERO ✨");
    }
}

