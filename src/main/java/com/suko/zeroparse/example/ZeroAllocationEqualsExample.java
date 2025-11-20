package com.suko.zeroparse.example;

import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;

/**
 * Example demonstrating zero-allocation string comparison using JsonStringView.equals(byte[]).
 * 
 * This is particularly useful in high-throughput scenarios like:
 * - Filtering trading symbols in WebSocket streams
 * - Matching exchange names without String allocation
 * - Comparing JSON field values against known constants
 */
public class ZeroAllocationEqualsExample {
    
    // Pre-defined byte arrays for common comparisons (avoid creating these repeatedly)
    private static final byte[] BTCUSDT = "BTCUSDT".getBytes();
    private static final byte[] ETHUSDT = "ETHUSDT".getBytes();
    private static final byte[] BINANCE = "Binance".getBytes();
    private static final byte[] BUY_SIDE = "BUY".getBytes();
    private static final byte[] SELL_SIDE = "SELL".getBytes();
    
    public static void main(String[] args) {
        // Example: Processing a stream of trade events
        String json = "{" +
            "\"symbol\":\"BTCUSDT\"," +
            "\"exchange\":\"Binance\"," +
            "\"side\":\"BUY\"," +
            "\"price\":\"27000.50\"," +
            "\"quantity\":\"0.125\"" +
            "}";
        
        JsonParser parser = new JsonParser();
        Buffer buffer = Buffer.buffer(json);
        
        // Process in pooled context for zero allocation
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject trade = ctx.parse(buffer).asObject();
            
            // Check symbol without allocating any strings
            JsonStringView symbol = trade.get("symbol").asString();
            if (symbol.equals(BTCUSDT)) {
                System.out.println("Processing BTC trade...");
                
                // Check side - no String allocation
                JsonStringView side = trade.get("side").asString();
                if (side.equals(BUY_SIDE)) {
                    double price = trade.get("price").asString().parseDouble();
                    double quantity = trade.get("quantity").asString().parseDouble();
                    System.out.printf("BUY %.8f BTC @ $%.2f%n", quantity, price);
                }
            } else if (symbol.equals(ETHUSDT)) {
                System.out.println("Processing ETH trade...");
            }
            
            // Check exchange
            JsonStringView exchange = trade.get("exchange").asString();
            if (exchange.equals(BINANCE)) {
                System.out.println("Trade from Binance");
            }
        }
        
        // Performance comparison example
        System.out.println("\nPerformance comparison:");
        
        // Simulate processing many messages
        int iterations = 1_000_000;
        
        // Traditional approach (allocates strings)
        long startTraditional = System.nanoTime();
        JsonObject obj = parser.parse(buffer).asObject();
        for (int i = 0; i < iterations; i++) {
            String sym = obj.get("symbol").toString(); // Allocates String
            if (sym.equals("BTCUSDT")) {
                // Process...
            }
        }
        long endTraditional = System.nanoTime();
        
        // Zero-allocation approach
        long startZeroAlloc = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            if (obj.get("symbol").asString().equals(BTCUSDT)) { // No allocation
                // Process...
            }
        }
        long endZeroAlloc = System.nanoTime();
        
        System.out.printf("Traditional approach: %.2f ms%n", 
            (endTraditional - startTraditional) / 1_000_000.0);
        System.out.printf("Zero-allocation approach: %.2f ms%n", 
            (endZeroAlloc - startZeroAlloc) / 1_000_000.0);
        System.out.printf("Speedup: %.2fx%n", 
            (double)(endTraditional - startTraditional) / (endZeroAlloc - startZeroAlloc));
    }
}
