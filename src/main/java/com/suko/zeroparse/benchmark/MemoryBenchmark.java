package com.suko.zeroparse.benchmark;

import io.vertx.core.buffer.Buffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.suko.zeroparse.JsonParser;
import com.suko.zeroparse.JsonValue;
import com.suko.zeroparse.JsonObject;
import com.suko.zeroparse.JsonParseContext;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for ZeroParse memory and allocation profiling.
 * 
 * Run with: mvn clean package && java -jar target/benchmarks.jar MemoryBenchmark -prof gc
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MemoryBenchmark {
    
    private Buffer testBuffer;
    private JsonParser parser;
    
    // Small message (typical crypto order book update)
    private Buffer smallBuffer;
    
    // Large message (deep nesting, many fields)
    private Buffer largeBuffer;
    
    // Reusable pooled parse context for pooled benchmarks
    private JsonParseContext pooledContext;
    @Setup
    public void setup() {
        // Small message: typical crypto order book snapshot
        String smallJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        smallBuffer = Buffer.buffer(smallJson);
        
        // Medium message
        String mediumJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        testBuffer = Buffer.buffer(mediumJson);
        
        // Large message: deeply nested structure
        String largeJson = buildLargeJson();
        largeBuffer = Buffer.buffer(largeJson);
        
        // Create parser ONCE
        parser = new JsonParser();
        // Create a reusable context that shares the same parser (single-threaded JMH)
        pooledContext = new JsonParseContext(parser, new com.suko.zeroparse.ViewPools());
    }
    
    private String buildLargeJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"level1\":{\"level2\":{\"level3\":{\"level4\":{\"level5\":{");
        sb.append("\"data\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i);
            sb.append(",\"price\":\"27000.").append(i);
            sb.append("\",\"qty\":\"1.234\",\"side\":\"buy\"}");
        }
        sb.append("]}}}}}}");
        return sb.toString();
    }
    
    // ===== POOLED PARSING BENCHMARKS (Garbage-Free with JsonParseContext) =====
    
    @Benchmark
    public JsonValue zeroparseSmallPooled(Blackhole bh) {
        pooledContext.close();
        return pooledContext.parse(smallBuffer);
    }
    
    @Benchmark
    public JsonValue zeroparseMediumPooled(Blackhole bh) {
        pooledContext.close();
        return pooledContext.parse(testBuffer);
    }
    
    @Benchmark
    public JsonValue zeroparseLargePooled(Blackhole bh) {
        pooledContext.close();
        return pooledContext.parse(largeBuffer);
    }
    
    @Benchmark
    public void zeroparseSmallPooledParseAndAccess(Blackhole bh) {
        pooledContext.close();
        JsonValue root = pooledContext.parse(smallBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            bh.consume(obj.get("action"));
            bh.consume(obj.get("data"));
        }
    }
    
    @Benchmark
    public void zeroparseMediumPooledParseAndAccess(Blackhole bh) {
        pooledContext.close();
        JsonValue root = pooledContext.parse(testBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            bh.consume(obj.get("action"));
            bh.consume(obj.get("data"));
        }
    }
    
    @Benchmark
    public void zeroparsePooledRepeatedFieldAccess(Blackhole bh) {
        pooledContext.close();
        JsonValue root = pooledContext.parse(testBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            // Access same fields multiple times
            for (int i = 0; i < 10; i++) {
                bh.consume(obj.get("action"));
                bh.consume(obj.get("ts"));
            }
        }
    }
    
    // ===== NUMBER CACHING BENCHMARKS =====
    
    @Benchmark
    public void zeroparsePooledNumberCachingRepeatedAccess(Blackhole bh) {
        pooledContext.close();
        JsonValue root = pooledContext.parse(testBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            JsonValue data = obj.get("data");
            if (data != null && data.isArray()) {
                JsonValue firstItem = data.asArray().get(0);
                if (firstItem != null && firstItem.isObject()) {
                    JsonObject item = firstItem.asObject();
                    JsonValue ts = item.get("ts");
                    if (ts != null && ts.isString()) {
                        // Parse the same number 10 times (tests caching + pooling)
                        for (int i = 0; i < 10; i++) {
                            bh.consume(ts.asString().parseLong());
                        }
                    }
                }
            }
        }
    }
    
    @Benchmark
    public void zeroparsePooledQuotedNumberParsing(Blackhole bh) {
        pooledContext.close();
        JsonValue root = pooledContext.parse(smallBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            JsonValue data = obj.get("data");
            if (data != null && data.isArray()) {
                JsonValue firstItem = data.asArray().get(0);
                if (firstItem != null && firstItem.isObject()) {
                    JsonObject item = firstItem.asObject();
                    JsonValue asks = item.get("asks");
                    if (asks != null && asks.isArray()) {
                        JsonValue firstAsk = asks.asArray().get(0);
                        if (firstAsk != null && firstAsk.isArray()) {
                            // Parse quoted price and qty (zero-copy + pooled)
                            bh.consume(firstAsk.asArray().get(0).asString().parseDouble());
                            bh.consume(firstAsk.asArray().get(1).asString().parseDouble());
                        }
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MemoryBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)  // Add GC profiler for allocation stats
                .build();
        
        new Runner(opt).run();
    }
}

