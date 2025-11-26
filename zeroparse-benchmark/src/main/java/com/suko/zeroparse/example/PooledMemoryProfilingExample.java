package com.suko.zeroparse.example;

import com.jprofiler.api.controller.Controller;
import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;

/**
 * JProfiler memory profiling example for ZeroParse with POOLED parsing.
 * 
 * <p>This example provides the same scenarios as MemoryProfilingExample but uses
 * JsonParseContext for garbage-free parsing. Compare the allocation profiles
 * between the two to see the memory reduction.</p>
 * 
 * <p>Run with JProfiler attached and use "Record Allocations" mode.</p>
 * 
 * <p><strong>Expected Results:</strong></p>
 * <ul>
 *   <li>Fewer JsonObject/JsonArray/JsonStringView/JsonNumberView allocations</li>
 *   <li>More ArrayList allocations (for tracking borrowed views)</li>
 *   <li>Overall reduction in total allocations for nested access patterns</li>
 * </ul>
 */
public class PooledMemoryProfilingExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ZeroParse POOLED Memory Profiling ===\n");
        
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

        // Controller.startCPURecording(true);
        
        // Profile different memory scenarios with POOLED parsing
        System.out.println("\n=== POOLED Scenario 1: Parse-Only (minimal field access) ===");
        Controller.addBookmark("Start: POOLED Scenario 1 - Parse-Only");
        profileParseOnly();
        Controller.addBookmark("End: POOLED Scenario 1 - Parse-Only");
        
        System.out.println("\n=== POOLED Scenario 2: Parse + Field Access (typical usage) ===");
        Controller.addBookmark("Start: POOLED Scenario 2 - Parse + Field Access");
        profileParseAndAccess();
        Controller.addBookmark("End: POOLED Scenario 2 - Parse + Field Access");
        
        System.out.println("\n=== POOLED Scenario 3: Repeated Field Access ===");
        Controller.addBookmark("Start: POOLED Scenario 3 - Repeated Field Access");
        profileRepeatedFieldAccess();
        Controller.addBookmark("End: POOLED Scenario 3 - Repeated Field Access");
        
        System.out.println("\n=== POOLED Scenario 4: Deep Nesting ===");
        Controller.addBookmark("Start: POOLED Scenario 4 - Deep Nesting");
        profileDeepNesting();
        Controller.addBookmark("End: POOLED Scenario 4 - Deep Nesting");
        
        System.out.println("\n=== POOLED Scenario 5: Large Arrays ===");
        Controller.addBookmark("Start: POOLED Scenario 5 - Large Arrays");
        profileLargeArrays();
        Controller.addBookmark("End: POOLED Scenario 5 - Large Arrays");
        
        System.out.println("\n=== POOLED Scenario 6: String Materialization ===");
        Controller.addBookmark("Start: POOLED Scenario 6 - String Materialization");
        profileStringMaterialization();
        Controller.addBookmark("End: POOLED Scenario 6 - String Materialization");
        
        System.out.println("\n=== POOLED Scenario 7: Mixed Workload ===");
        Controller.addBookmark("Start: POOLED Scenario 7 - Mixed Workload");
        profileMixedWorkload();
        Controller.addBookmark("End: POOLED Scenario 7 - Mixed Workload");

        // Controller.stopCPURecording();
        
        System.out.println("\n=== Memory Profiling Complete ===");
        System.out.println("Stop JProfiler recording and compare with non-pooled results.");
        System.out.println("\nKey metrics to compare:");
        System.out.println("  - JsonObject/JsonArray/JsonStringView/JsonNumberView allocations (should be MUCH lower)");
        System.out.println("  - ArrayList allocations (will be higher due to tracking)");
        System.out.println("  - Total allocation rate (should be lower overall)");
        System.out.println("  - GC Activity (should be reduced)");
    }
    
    private static void warmUp() {
        String json = "{\"action\":\"snapshot\",\"data\":{\"price\":\"27000.5\",\"qty\":\"1.234\"}}";
        Buffer buffer = Buffer.buffer(json);
        
        // Run 50k iterations to warm up JIT
        for (int i = 0; i < 50_000; i++) {
            
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                if (root.isObject()) {
                    JsonObject obj = root.asObject();
                    obj.get("action");
                }
            }
        }
    }
    
    /**
     * Scenario 1: Parse-Only (minimal field access) - POOLED
     * Tests: Base parser allocations, AST storage, cursor objects, pool overhead
     */
    private static void profileParseOnly() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse 100k times with minimal access - POOLED
        for (int i = 0; i < 100_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                // Just parse, don't access fields (tests parser + pool allocations)
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Parse-Only", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 2: Parse + Field Access (typical usage) - POOLED
     * Tests: Pooled JsonObject reuse, nested view allocation reduction
     */
    private static void profileParseAndAccess() {
        String json = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"]],\"bids\":[[\"27000.0\",\"2.710\"]]}],\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and access fields - POOLED
        for (int i = 0; i < 100_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                JsonObject obj = root.asObject();
                
                // Access top-level fields (nested views come from pool)
                JsonValue action = obj.get("action");
                JsonValue ts = obj.get("ts");
                JsonObject arg = obj.getObject("arg");
                
                if (arg != null) {
                    arg.get("instType");
                    arg.get("channel");
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Parse + Field Access", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 3: Repeated Field Access - POOLED
     * Tests: Pool effectiveness with repeated nested access
     */
    private static void profileRepeatedFieldAccess() {
        String json = "{\"action\":\"snapshot\",\"price\":\"27000.5\",\"qty\":\"1.234\",\"ts\":1695716059516}";
        Buffer buffer = Buffer.buffer(json);
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse once, access same fields many times - POOLED
        for (int i = 0; i < 50_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                JsonObject obj = root.asObject();
                
                // Access same fields 10 times
                for (int j = 0; j < 10; j++) {
                    obj.get("action");
                    obj.get("price");
                    obj.get("qty");
                    obj.get("ts");
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Repeated Field Access", startTime, endTime, startMem, endMem, 50_000);
    }
    
    /**
     * Scenario 4: Deep Nesting - POOLED
     * Tests: Pool effectiveness with deeply nested structures
     */
    private static void profileDeepNesting() {
        String json = "{\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"l5\":{\"l6\":{\"l7\":{\"l8\":{\"price\":\"27000.5\",\"qty\":\"1.234\"}}}}}}}}";
        Buffer buffer = Buffer.buffer(json);
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse deeply nested structure - POOLED
        for (int i = 0; i < 100_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                JsonObject obj = root.asObject();
                
                // Navigate to deepest level (all intermediate objects from pool)
                JsonObject current = obj;
                for (int j = 1; j <= 8 && current != null; j++) {
                    current = current.getObject("l" + j);
                }
                
                if (current != null) {
                    current.get("price");
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Deep Nesting", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 5: Large Arrays - POOLED
     * Tests: Pool effectiveness with many array elements
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
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and iterate large arrays - POOLED
        for (int i = 0; i < 25_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                JsonObject obj = root.asObject();
                JsonArray data = obj.getArray("data");
                
                if (data != null) {
                    // Iterate all elements (all from pool)
                    for (int j = 0; j < data.size(); j++) {
                        JsonObject item = data.get(j).asObject();
                        item.get("id");
                        item.get("price");
                    }
                }
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Large Arrays", startTime, endTime, startMem, endMem, 25_000);
    }
    
    /**
     * Scenario 6: String Materialization - POOLED
     * Tests: Pool doesn't affect string materialization (same allocations)
     */
    private static void profileStringMaterialization() {
        String json = "{\"symbol\":\"BTCUSDT\",\"exchange\":\"binance\",\"type\":\"limit\",\"side\":\"buy\",\"price\":\"27000.50\",\"quantity\":\"1.23456789\"}";
        Buffer buffer = Buffer.buffer(json);

        StringBuilder sb = new StringBuilder(1024);
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Parse and materialize all string values - POOLED
        for (int i = 0; i < 100_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                JsonValue root = ctx.parse(buffer);
                JsonObject obj = root.asObject();
                sb.setLength(0);
                // Materialize all strings (calls toString())
                // Note: String allocations will be same as non-pooled
                String symbol = obj.get("symbol").toString();
                String exchange = obj.get("exchange").toString();
                String type = obj.get("type").toString();
                String side = obj.get("side").toString();
                String price = obj.get("price").toString();
                String quantity = obj.get("quantity").toString();
            }
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED String Materialization", startTime, endTime, startMem, endMem, 100_000);
    }
    
    /**
     * Scenario 7: Mixed Workload - POOLED
     * Tests: Pool effectiveness in realistic trading scenarios
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
        
        Controller.startAllocRecording(false);
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.nanoTime();
        
        // Simulate mixed message stream - POOLED
        for (int i = 0; i < 100_000; i++) {
            try (JsonParseContext ctx = new JsonParseContext()) {
                Buffer buffer = buffers[i % buffers.length];
                JsonValue root = ctx.parse(buffer);
                
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
        }
        
        long endTime = System.nanoTime();
        Controller.stopAllocRecording();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        printStats("POOLED Mixed Workload", startTime, endTime, startMem, endMem, 100_000);
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

