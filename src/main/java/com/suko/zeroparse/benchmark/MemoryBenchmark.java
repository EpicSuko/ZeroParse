package com.suko.zeroparse.benchmark;

import io.vertx.core.buffer.Buffer;
import me.doubledutch.lazyjson.LazyObject;
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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

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
    private String testString;
    private byte[] testBytes;
    private JsonParser parser;
    
    // Small message (typical crypto order book update)
    private Buffer smallBuffer;
    private String smallString;
    private byte[] smallBytes;
    
    // Large message (deep nesting, many fields)
    private Buffer largeBuffer;
    private String largeString;
    private byte[] largeBytes;
    
    @Setup
    public void setup() {
        // Small message: typical crypto order book snapshot
        String smallJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        smallBuffer = Buffer.buffer(smallJson);
        smallString = smallJson;
        smallBytes = smallJson.getBytes();
        
        // Medium message
        String mediumJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        testBuffer = Buffer.buffer(mediumJson);
        testString = mediumJson;
        testBytes = mediumJson.getBytes();
        
        // Large message: deeply nested structure
        String largeJson = buildLargeJson();
        largeBuffer = Buffer.buffer(largeJson);
        largeString = largeJson;
        largeBytes = largeJson.getBytes();
        
        // Create parser ONCE
        parser = new JsonParser();
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

    // ===== Parse Only (no field access) =====
    
    @Benchmark
    public JsonValue zeroparseSmallParseOnly(Blackhole bh) {
        return parser.parse(smallBuffer);
    }
    
    @Benchmark
    public JSONObject fastjson2SmallParseOnly(Blackhole bh) {
        return JSON.parseObject(smallBytes);
    }
    
    @Benchmark
    public LazyObject lazyjsonSmallParseOnly(Blackhole bh) {
        return new LazyObject(smallString);
    }
    
    @Benchmark
    public JsonValue zeroparseMediumParseOnly(Blackhole bh) {
        return parser.parse(testBuffer);
    }
    
    @Benchmark
    public JSONObject fastjson2MediumParseOnly(Blackhole bh) {
        return JSON.parseObject(testBytes);
    }
    
    @Benchmark
    public LazyObject lazyjsonMediumParseOnly(Blackhole bh) {
        return new LazyObject(testString);
    }
    
    @Benchmark
    public JsonValue zeroparseLargeParseOnly(Blackhole bh) {
        return parser.parse(largeBuffer);
    }
    
    @Benchmark
    public JSONObject fastjson2LargeParseOnly(Blackhole bh) {
        return JSON.parseObject(largeBytes);
    }
    
    @Benchmark
    public LazyObject lazyjsonLargeParseOnly(Blackhole bh) {
        return new LazyObject(largeString);
    }
    
    // ===== Parse + Field Access (realistic usage) =====
    
    @Benchmark
    public void zeroparseSmallParseAndAccess(Blackhole bh) {
        JsonValue root = parser.parse(smallBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            bh.consume(obj.get("action"));
            JsonValue arg = obj.get("arg");
            if (arg != null && arg.isObject()) {
                JsonObject argObj = arg.asObject();
                bh.consume(argObj.get("instType"));
                bh.consume(argObj.get("channel"));
                bh.consume(argObj.get("instId"));
            }
        }
    }
    
    @Benchmark
    public void fastjson2SmallParseAndAccess(Blackhole bh) {
        JSONObject obj = JSON.parseObject(smallBytes);
        bh.consume(obj.get("action"));
        JSONObject arg = obj.getJSONObject("arg");
        if (arg != null) {
            bh.consume(arg.get("instType"));
            bh.consume(arg.get("channel"));
            bh.consume(arg.get("instId"));
        }
    }
    
    @Benchmark
    public void lazyjsonSmallParseAndAccess(Blackhole bh) {
        LazyObject obj = new LazyObject(smallString);
        bh.consume(obj.getString("action"));
        LazyObject arg = obj.getJSONObject("arg");
        if (arg != null) {
            bh.consume(arg.getString("instType"));
            bh.consume(arg.getString("channel"));
            bh.consume(arg.getString("instId"));
        }
    }
    
    @Benchmark
    public void zeroparseMediumParseAndAccess(Blackhole bh) {
        JsonValue root = parser.parse(testBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            bh.consume(obj.get("action"));
            bh.consume(obj.get("ts"));
        }
    }
    
    @Benchmark
    public void fastjson2MediumParseAndAccess(Blackhole bh) {
        JSONObject obj = JSON.parseObject(testBytes);
        bh.consume(obj.get("action"));
        bh.consume(obj.get("ts"));
    }
    
    @Benchmark
    public void lazyjsonMediumParseAndAccess(Blackhole bh) {
        LazyObject obj = new LazyObject(testString);
        bh.consume(obj.getString("action"));
        bh.consume(obj.getLong("ts"));
    }
    
    // ===== Repeated Field Access (test field cache) =====
    
    @Benchmark
    public void zeroparseRepeatedFieldAccess(Blackhole bh) {
        JsonValue root = parser.parse(testBuffer);
        if (root.isObject()) {
            JsonObject obj = root.asObject();
            // Access same fields multiple times (tests cache effectiveness)
            for (int i = 0; i < 10; i++) {
                bh.consume(obj.get("action"));
                bh.consume(obj.get("ts"));
            }
        }
    }
    
    @Benchmark
    public void fastjson2RepeatedFieldAccess(Blackhole bh) {
        JSONObject obj = JSON.parseObject(testBytes);
        for (int i = 0; i < 10; i++) {
            bh.consume(obj.get("action"));
            bh.consume(obj.get("ts"));
        }
    }
    
    @Benchmark
    public void lazyjsonRepeatedFieldAccess(Blackhole bh) {
        LazyObject obj = new LazyObject(testString);
        for (int i = 0; i < 10; i++) {
            bh.consume(obj.getString("action"));
            bh.consume(obj.getLong("ts"));
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

