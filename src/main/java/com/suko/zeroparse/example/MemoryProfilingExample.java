package com.suko.zeroparse.example;

import com.jprofiler.api.controller.Controller;
import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;

/**
 * JProfiler memory profiling example for ZeroParse.
 * 
 * <p>This example provides scenarios specifically designed for memory profiling:
 * - Allocation hot spots
 * - Object lifecycle analysis
 * - Memory leak detection
 * - GC pressure patterns
 * </p>
 * 
 * <p>Run with JProfiler attached and use "Record Allocations" mode.</p>
 */
public class MemoryProfilingExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ZeroParse Memory Profiling ===\n");
        
        // Warm up JIT compiler
        System.out.println("Phase 1: Warming up JIT compiler...");
        warmUp();
        System.out.println("Warm-up complete.\n");
        
        // Force GC before profiling
        System.gc();
        try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
        
        // Wait for user to start JProfiler memory recording
        System.out.println("Start JProfiler Memory Recording (Record Allocations), then press Enter...");
        System.in.read();
        
        // Profile different memory scenarios (using bookmarks to mark each)
        System.out.println("\n=== Scenario 1: Parse-Only (minimal field access) ===");
        Controller.addBookmark("Start: Scenario 1 - Parse-Only");
        profileParseOnly();
        Controller.addBookmark("End: Scenario 1 - Parse-Only");
        
        System.out.println("\n=== Scenario 2: Parse + Field Access (typical usage) ===");
        Controller.addBookmark("Start: Scenario 2 - Parse + Field Access");
        profileParseAndAccess();
        Controller.addBookmark("End: Scenario 2 - Parse + Field Access");
        
        System.out.println("\n=== Scenario 3: Repeated Field Access (tests field cache) ===");
        Controller.addBookmark("Start: Scenario 3 - Repeated Field Access");
        profileRepeatedFieldAccess();
        Controller.addBookmark("End: Scenario 3 - Repeated Field Access");
        
        System.out.println("\n=== Scenario 4: Deep Nesting (AST depth) ===");
        Controller.addBookmark("Start: Scenario 4 - Deep Nesting");
        profileDeepNesting();
        Controller.addBookmark("End: Scenario 4 - Deep Nesting");
        
        System.out.println("\n=== Scenario 5: Large Arrays (many elements) ===");
        Controller.addBookmark("Start: Scenario 5 - Large Arrays");
        profileLargeArrays();
        Controller.addBookmark("End: Scenario 5 - Large Arrays");
        
        System.out.println("\n=== Scenario 6: String Materialization ===");
        Controller.addBookmark("Start: Scenario 6 - String Materialization");
        profileStringMaterialization();
        Controller.addBookmark("End: Scenario 6 - String Materialization");
        
        System.out.println("\n=== Scenario 7: Mixed Workload (realistic trading) ===");
        Controller.addBookmark("Start: Scenario 7 - Mixed Workload");
        profileMixedWorkload();
        Controller.addBookmark("End: Scenario 7 - Mixed Workload");
        
        System.out.println("\n=== Memory Profiling Complete ===");
        System.out.println("Stop JProfiler recording and analyze allocations.");
        System.out.println("\nKey metrics to check:");
        System.out.println("  - Allocation Hot Spots");
        System.out.println("  - Live Objects (check for leaks)");
        System.out.println("  - Allocation Call Tree");
        System.out.println("  - GC Activity");
    }
    
    private static void warmUp() {
        String json = "{\"action\":\"snapshot\",\"data\":{\"price\":\"27000.5\",\"qty\":\"1.234\"}}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        // Run 50k iterations to warm up JIT
        for (int i = 0; i < 50_000; i++) {
            JsonValue root = parser.parse(buffer);
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                obj.get("action");
            }
        }
    }
    
    /**
     * Scenario 1: Parse-Only (minimal field access)
     * Tests: Base parser allocations, AST storage, cursor objects
     */
    private static void profileParseOnly() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse 100k times with minimal access
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            // Just parse, don't access fields (tests parser allocations only)
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Parse-Only", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 2: Parse + Field Access (typical usage)
     * Tests: JsonObject creation, field lookup, value materialization
     */
    private static void profileParseAndAccess() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"]],\"bids\":[[\"27000.0\",\"2.710\"]]}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and access fields (typical usage)
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            
            // Access top-level fields
            JsonValue action = obj.get("action");
            JsonValue ts = obj.get("ts");
            JsonObject arg = obj.getObject("arg");
            
            if (arg != null) {
                arg.get("instType");
                arg.get("channel");
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Parse + Field Access", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 3: Repeated Field Access (tests field cache)
     * Tests: HashMap initialization, cache hits, String.getBytes()
     */
    private static void profileRepeatedFieldAccess() {
        String json = "{\"action\":\"snapshot\",\"price\":\"27000.5\",\"qty\":\"1.234\",\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse once, access same fields many times
        for (int i = 0; i < 50_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            
            // Access same fields 10 times (should hit cache after first)
            for (int j = 0; j < 10; j++) {
                obj.get("action");
                obj.get("price");
                obj.get("qty");
                obj.get("ts");
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Repeated Field Access", startTime, endTime, startMem, endMem, 50_000);
    }
    
    /**
     * Scenario 4: Deep Nesting (AST depth)
     * Tests: AST growth with nesting, stack usage
     */
    private static void profileDeepNesting() {
        String json = "{\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"l5\":{\"l6\":{\"l7\":{\"l8\":{\"price\":\"27000.5\",\"qty\":\"1.234\"}}}}}}}}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse deeply nested structure
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            
            // Navigate to deepest level
            JsonObject current = obj;
            for (int j = 1; j <= 8 && current != null; j++) {
                current = current.getObject("l" + j);
            }
            
            if (current != null) {
                current.get("price");
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Deep Nesting", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 5: Large Arrays (many elements)
     * Tests: Array element access, iteration overhead
     */
    private static void profileLargeArrays() {
        // Build array with 100 elements
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i).append(",\"price\":\"27000.").append(i).append("\"}");
        }
        sb.append("]}");
        
        String json = sb.toString();
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and iterate large arrays
        for (int i = 0; i < 25_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            JsonArray data = obj.getArray("data");
            
            if (data != null) {
                // Iterate all elements
                for (int j = 0; j < data.size(); j++) {
                    JsonObject item = data.get(j).asObject();
                    item.get("id");
                    item.get("price");
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Large Arrays", startTime, endTime, startMem, endMem, 25_000);
    }
    
    /**
     * Scenario 6: String Materialization
     * Tests: Utf8Slice.toString(), String allocation patterns
     */
    private static void profileStringMaterialization() {
        String json = "{\"symbol\":\"BTCUSDT\",\"exchange\":\"binance\",\"type\":\"limit\",\"side\":\"buy\",\"price\":\"27000.50\",\"quantity\":\"1.23456789\"}";
        Buffer buffer = Buffer.buffer(json);
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and materialize all string values
        for (int i = 0; i < 100_000; i++) {
            JsonValue root = parser.parse(buffer);
            JsonObject obj = root.asObject();
            
            // Materialize all strings (calls toString())
            String symbol = obj.get("symbol").toString();
            String exchange = obj.get("exchange").toString();
            String type = obj.get("type").toString();
            String side = obj.get("side").toString();
            String price = obj.get("price").toString();
            String quantity = obj.get("quantity").toString();
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("String Materialization", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 7: Mixed Workload (realistic trading)
     * Tests: Real-world usage patterns with varied access
     */
    private static void profileMixedWorkload() {
        // Mix of different message types
        String[] messages = {
            "{\"action\":\"snapshot\",\"data\":{\"price\":\"27000.5\",\"qty\":\"1.234\"}}",
            "{\"stream\":\"btcusdt@trade\",\"data\":{\"p\":\"27001.0\",\"q\":\"0.5\"}}",
            "{\"channel\":\"depth\",\"levels\":[[\"27000\",\"1.0\"],[\"27001\",\"2.0\"]]}",
            "{\"type\":\"order\",\"order\":{\"id\":123,\"price\":\"27000.5\",\"qty\":\"1.0\",\"side\":\"buy\"}}"
        };
        
        Buffer[] buffers = new Buffer[messages.length];
        for (int i = 0; i < messages.length; i++) {
            buffers[i] = Buffer.buffer(messages[i]);
        }
        
        JsonParser parser = new JsonParser();
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Simulate mixed message stream
        for (int i = 0; i < 100_000; i++) {
            Buffer buffer = buffers[i % buffers.length];
            JsonValue root = parser.parse(buffer);
            
            if (root.isObject()) {
                JsonObject obj = root.asObject();
                
                // Different access patterns based on message type
                switch (i % 4) {
                    case 0: // Snapshot
                        obj.get("action");
                        JsonObject data = obj.getObject("data");
                        if (data != null) {
                            data.get("price");
                            data.get("qty");
                        }
                        break;
                    case 1: // Trade
                        obj.get("stream");
                        JsonObject tradeData = obj.getObject("data");
                        if (tradeData != null) {
                            tradeData.get("p");
                            tradeData.get("q");
                        }
                        break;
                    case 2: // Depth
                        obj.get("channel");
                        JsonArray levels = obj.getArray("levels");
                        if (levels != null && levels.size() > 0) {
                            levels.get(0);
                        }
                        break;
                    case 3: // Order
                        obj.get("type");
                        JsonObject order = obj.getObject("order");
                        if (order != null) {
                            order.get("price");
                            order.get("side");
                        }
                        break;
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("Mixed Workload", startTime, endTime, startMem, endMem, 100_000);
    }
    
    private static void printStats(String scenario, long startTime, long endTime, long startMem, long endMem, int iterations) {
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);
        double memoryUsedMB = (endMem - startMem) / (1024.0 * 1024.0);
        double bytesPerOp = (endMem - startMem) / (double) iterations;
        
        System.out.printf("  Scenario: %s\n", scenario);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f ops/sec\n", throughput);
        System.out.printf("  Memory Used: %.2f MB\n", memoryUsedMB);
        System.out.printf("  Bytes/Operation: %.1f B\n", bytesPerOp);
    }
}

