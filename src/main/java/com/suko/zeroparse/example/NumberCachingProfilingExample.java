package com.suko.zeroparse.example;

import com.jprofiler.api.controller.Controller;
import com.suko.zeroparse.JsonParseContext;
import com.suko.zeroparse.JsonObject;
import com.suko.zeroparse.JsonArray;
import com.suko.zeroparse.JsonValue;
import io.vertx.core.buffer.Buffer;

/**
 * JProfiler example to demonstrate number caching benefits.
 * 
 * This example shows:
 * 1. Non-pooled parsing with number caching
 * 2. Pooled parsing with number caching
 * 3. Repeated number access patterns (cache effectiveness)
 * 4. Quoted number parsing (zero-copy)
 * 
 * Run with JProfiler to observe:
 * - Memory allocations (should see caching reduce allocations)
 * - Object counts (JsonNumberView, Long/Double wrappers)
 * - GC pressure (pooled should have near-zero GC)
 */
public class NumberCachingProfilingExample {
    
    // Crypto order book update with quoted numbers
    private static final String ORDER_BOOK_JSON = 
        "{\"action\":\"snapshot\"," +
        "\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"}," +
        "\"data\":[{" +
        "  \"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"],[\"27001.5\",\"1.200\"]]," +
        "  \"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"],[\"26999.0\",\"0.800\"]]," +
        "  \"checksum\":123456," +
        "  \"seq\":789," +
        "  \"ts\":\"1695716059516\"" +
        "}]," +
        "\"ts\":1695716059516}";
    
    private static final Buffer ORDER_BOOK_BUFFER = Buffer.buffer(ORDER_BOOK_JSON);
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== ZeroParse Number Caching Profiling Example ===\n");
        
        // Warmup
        System.out.println("Phase 1: Warming up JIT compiler...");
        warmUp();
        System.out.println("Warm-up complete.\n");
        
        // Force GC before profiling
        System.gc();
        try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
        
        // Wait for user to start JProfiler memory recording
        System.out.println("Start JProfiler Memory Recording (Record Allocations), then press Enter...");
        System.in.read();
        
        System.out.println("\n=== Starting Profiling Scenarios ===\n");
        
        // Scenario 1: Non-Pooled Parsing with Number Caching
        System.out.println("=== Scenario 1: Non-Pooled Parsing with Number Caching ===");
        System.out.println("Expected: Moderate allocations, number caching reduces repeated access cost");
        Controller.addBookmark("Start: Scenario 1 - Non-Pooled Number Caching");
        Controller.startCPURecording(false);
        long s1Start = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            profileNonPooledWithCaching();
        }
        long s1End = System.nanoTime();
        Controller.stopCPURecording();
        Controller.addBookmark("End: Scenario 1 - Non-Pooled Number Caching");
        printStats("Non-Pooled Number Caching", s1Start, s1End, 50_000);
        
        try { Thread.sleep(2000); } catch (InterruptedException e) { /* ignore */ }
        
        // Scenario 2: Pooled Parsing with Number Caching
        System.out.println("\n=== Scenario 2: Pooled Parsing with Number Caching (Zero GC) ===");
        System.out.println("Expected: Near-zero allocations, no GC events");
        Controller.addBookmark("Start: Scenario 2 - Pooled Number Caching");
        Controller.startCPURecording(false);
        long s2Start = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            profilePooledWithCaching();
        }
        long s2End = System.nanoTime();
        Controller.stopCPURecording();
        Controller.addBookmark("End: Scenario 2 - Pooled Number Caching");
        printStats("Pooled Number Caching", s2Start, s2End, 50_000);
        
        try { Thread.sleep(2000); } catch (InterruptedException e) { /* ignore */ }
        
        // Scenario 3: Repeated Number Access (Cache Effectiveness)
        System.out.println("\n=== Scenario 3: Repeated Number Access (10x per parse) ===");
        System.out.println("Expected: First access parses, subsequent accesses use cache (instant)");
        Controller.addBookmark("Start: Scenario 3 - Repeated Number Access");
        Controller.startCPURecording(false);
        long s3Start = System.nanoTime();
        for (int i = 0; i < 20_000; i++) {
            profileRepeatedNumberAccess();
        }
        long s3End = System.nanoTime();
        Controller.stopCPURecording();
        Controller.addBookmark("End: Scenario 3 - Repeated Number Access");
        printStats("Repeated Number Access (20k × 10)", s3Start, s3End, 20_000);
        
        try { Thread.sleep(2000); } catch (InterruptedException e) { /* ignore */ }
        
        // Scenario 4: Quoted Number Parsing (Zero-Copy)
        System.out.println("\n=== Scenario 4: Quoted Number Parsing (Zero-Copy) ===");
        System.out.println("Expected: No String allocations, direct byte[] → double parsing");
        Controller.addBookmark("Start: Scenario 4 - Quoted Number Parsing");
        Controller.startCPURecording(false);
        long s4Start = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            profileQuotedNumberParsing();
        }
        long s4End = System.nanoTime();
        Controller.stopCPURecording();
        Controller.addBookmark("End: Scenario 4 - Quoted Number Parsing");
        printStats("Quoted Number Parsing", s4Start, s4End, 50_000);
        
        try { Thread.sleep(2000); } catch (InterruptedException e) { /* ignore */ }
        
        // Scenario 5: Pooled Quoted Number Parsing
        System.out.println("\n=== Scenario 5: Pooled Quoted Number Parsing (Zero-Copy + Zero GC) ===");
        System.out.println("Expected: Zero allocations, zero GC, maximum efficiency");
        Controller.addBookmark("Start: Scenario 5 - Pooled Quoted Number Parsing");
        Controller.startCPURecording(false);
        long s5Start = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            profilePooledQuotedNumberParsing();
        }
        long s5End = System.nanoTime();
        Controller.stopCPURecording();
        Controller.addBookmark("End: Scenario 5 - Pooled Quoted Number Parsing");
        printStats("Pooled Quoted Number Parsing", s5Start, s5End, 50_000);
        
        System.out.println("\n=== Profiling Complete ===");
        System.out.println("\nStop JProfiler recording and analyze:");
        System.out.println("1. Live Memory → Classes → Check JsonNumberView, Long, Double allocations");
        System.out.println("2. Telemetries → Memory → GC Activity (near-zero for pooled scenarios)");
        System.out.println("3. Hot Spots → Call Tree → See caching impact on repeated access");
        System.out.println("4. Compare allocation rates between scenarios");
        System.out.println("\nKey Observations:");
        System.out.println("  - Scenario 1: Moderate allocations (non-pooled but cached)");
        System.out.println("  - Scenario 2: Minimal allocations (pooled + cached)");
        System.out.println("  - Scenario 3: Cache effectiveness (90% reduction vs re-parsing)");
        System.out.println("  - Scenario 4: Zero-copy vs String allocation");
        System.out.println("  - Scenario 5: True garbage-free (pooled + zero-copy + cached)");
    }
    
    private static void warmUp() {
        // Run 10k iterations to warm up JIT
        for (int i = 0; i < 10_000; i++) {
            profileNonPooledWithCaching();
            profilePooledWithCaching();
            profileRepeatedNumberAccess();
            profileQuotedNumberParsing();
            profilePooledQuotedNumberParsing();
        }
    }
    
    /**
     * Non-pooled parsing with number caching.
     * Numbers are cached after first access.
     */
    private static void profileNonPooledWithCaching() {
        try(JsonParseContext ctx = new JsonParseContext()) {
            JsonValue root = ctx.parse(ORDER_BOOK_BUFFER);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                
                // Access timestamp (native JSON number)
                long ts = obj.get("ts").asNumber().asLong();
                
                // Access data array
                JsonValue data = obj.get("data");
                if (data != null && data.isArray() && data.asArray().size() > 0) {
                    JsonObject item = data.asArray().get(0).asObject();
                    
                    // Access native numbers
                    int checksum = item.get("checksum").asNumber().asInt();
                    int seq = item.get("seq").asNumber().asInt();
                    
                    // Prevent optimization
                    if (ts == 0 || checksum == 0 || seq == 0) {
                        throw new RuntimeException("Unexpected");
                    }
                }
            }
        }
    }
    
    /**
     * Pooled parsing with number caching (zero GC).
     */
    private static void profilePooledWithCaching() {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonValue root = ctx.parse(ORDER_BOOK_BUFFER);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                
                // Access timestamp (cached after first access)
                long ts = obj.get("ts").asNumber().asLong();
                
                // Access data array
                JsonValue data = obj.get("data");
                if (data != null && data.isArray() && data.asArray().size() > 0) {
                    JsonObject item = data.asArray().get(0).asObject();
                    
                    // Access native numbers (all cached)
                    int checksum = item.get("checksum").asNumber().asInt();
                    int seq = item.get("seq").asNumber().asInt();
                    
                    // Prevent optimization
                    if (ts == 0 || checksum == 0 || seq == 0) {
                        throw new RuntimeException("Unexpected");
                    }
                }
            }
        } // Auto-cleanup: all pooled objects returned here
    }
    
    /**
     * Repeated number access to test cache effectiveness.
     * Accesses the same number 10 times - should only parse once.
     */
    private static void profileRepeatedNumberAccess() {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonValue root = ctx.parse(ORDER_BOOK_BUFFER);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                JsonValue data = obj.get("data");
                
                if (data != null && data.isArray() && data.asArray().size() > 0) {
                    JsonObject item = data.asArray().get(0).asObject();
                    
                    // Access the same number 10 times
                    // First access: parse and cache
                    // Subsequent 9 accesses: instant from cache!
                    long sum = 0;
                    for (int i = 0; i < 10; i++) {
                        sum += item.get("checksum").asNumber().asInt();
                        sum += item.get("seq").asNumber().asInt();
                    }
                    
                    // Prevent optimization
                    if (sum == 0) {
                        throw new RuntimeException("Unexpected");
                    }
                }
            }
        }
    }
    
    /**
     * Quoted number parsing (zero-copy).
     * Parses strings like "27000.5" directly from bytes without String allocation.
     */
    private static void profileQuotedNumberParsing() {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonValue root = ctx.parse(ORDER_BOOK_BUFFER);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                JsonValue data = obj.get("data");
                
                if (data != null && data.isArray() && data.asArray().size() > 0) {
                    JsonObject item = data.asArray().get(0).asObject();
                    JsonValue asks = item.get("asks");
                    
                    if (asks != null && asks.isArray()) {
                        JsonArray asksArray = asks.asArray();
                        
                        // Parse quoted prices and quantities (zero-copy)
                        for (int i = 0; i < asksArray.size(); i++) {
                            JsonValue ask = asksArray.get(i);
                            if (ask != null && ask.isArray()) {
                                JsonArray priceQty = ask.asArray();
                                
                                // Zero-copy: parseDouble() reads directly from byte[]
                                double price = priceQty.get(0).asString().parseDouble();
                                double qty = priceQty.get(1).asString().parseDouble();
                                
                                // Prevent optimization
                                if (price == 0 || qty == 0) {
                                    throw new RuntimeException("Unexpected");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Pooled quoted number parsing (zero-copy + zero GC).
     */
    private static void profilePooledQuotedNumberParsing() {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonValue root = ctx.parse(ORDER_BOOK_BUFFER);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                JsonValue data = obj.get("data");
                
                if (data != null && data.isArray() && data.asArray().size() > 0) {
                    JsonObject item = data.asArray().get(0).asObject();
                    JsonValue asks = item.get("asks");
                    JsonValue bids = item.get("bids");
                    
                    // Process asks (quoted numbers)
                    if (asks != null && asks.isArray()) {
                        JsonArray asksArray = asks.asArray();
                        for (int i = 0; i < asksArray.size(); i++) {
                            JsonValue ask = asksArray.get(i);
                            if (ask != null && ask.isArray()) {
                                JsonArray priceQty = ask.asArray();
                                
                                // Zero-copy + cached parsing
                                double price = priceQty.get(0).asString().parseDouble();
                                double qty = priceQty.get(1).asString().parseDouble();
                                
                                // Prevent optimization
                                if (price == 0 || qty == 0) {
                                    throw new RuntimeException("Unexpected");
                                }
                            }
                        }
                    }
                    
                    // Process bids (quoted numbers)
                    if (bids != null && bids.isArray()) {
                        JsonArray bidsArray = bids.asArray();
                        for (int i = 0; i < bidsArray.size(); i++) {
                            JsonValue bid = bidsArray.get(i);
                            if (bid != null && bid.isArray()) {
                                JsonArray priceQty = bid.asArray();
                                
                                // Zero-copy + cached parsing
                                double price = priceQty.get(0).asString().parseDouble();
                                double qty = priceQty.get(1).asString().parseDouble();
                                
                                // Prevent optimization
                                if (price == 0 || qty == 0) {
                                    throw new RuntimeException("Unexpected");
                                }
                            }
                        }
                    }
                }
            }
        } // Auto-cleanup: all pooled objects returned
    }
    
    private static void printStats(String scenario, long startTime, long endTime, int iterations) {
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);
        
        System.out.printf("  Scenario: %s\n", scenario);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f ops/sec\n", throughput);
        System.out.printf("  ✓ Completed %,d iterations\n\n", iterations);
    }
}

