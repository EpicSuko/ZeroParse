package com.suko.zeroparse.benchmark;

import io.vertx.core.buffer.Buffer;
import me.doubledutch.lazyjson.LazyObject;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.suko.zeroparse.JsonParser;
import com.suko.zeroparse.JsonValue;
import com.suko.zeroparse.JsonArray;
import com.suko.zeroparse.JsonParseContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for ZeroParse performance.
 */
@BenchmarkMode(Mode.Throughput)
// @BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
// @OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ZeroParseBenchmark {
    
    private Buffer testBuffer;
    private String testString;
    private byte[] testBytes;
    private JsonParser parser;
    // Reusable context + pools for pooled benchmarks (single-threaded JMH usage)
    private JsonParseContext pooledContext;
    
    @Setup
    public void setup() {
        // Create test JSON data
        String testJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        testBuffer = Buffer.buffer(testJson);
        testString = testBuffer.toString();
        testBytes = testBuffer.getBytes();
        
        // Create parser ONCE in setup
        parser = new JsonParser();
        // Create a reusable context that shares the same parser (single-threaded JMH)
        pooledContext = new JsonParseContext(parser, new com.suko.zeroparse.ViewPools());
    }

    @Benchmark
    public void baseline() {}
    
    @Benchmark
    public JsonValue parseFromBufferStack() {
        // Just parse - no setup overhead per iteration
        return parser.parse(testBuffer);
    }
    
    @Benchmark
    public JsonValue parseFromStringStack() {
        // Just parse - no setup overhead per iteration
        return parser.parse(testString);
    }
    
    @Benchmark
    public JsonValue parseFromByteArrayStack() {
        // Just parse - no setup overhead per iteration
        return parser.parse(testBytes, 0, testBytes.length);
    }

    // FastJson2 benchmarks for comparison
    @Benchmark
    public JSONObject fastjson2ParseFromString() {
        return JSON.parseObject(testString);
    }
    
    @Benchmark
    public JSONObject fastjson2ParseFromBytes() {
        return JSON.parseObject(testBytes);
    }

    // LazyJSON benchmarks for comparison
    @Benchmark
    public me.doubledutch.lazyjson.LazyObject lazyjsonParseFromString() {
        return new LazyObject(testString);
    }
    
    // ===== POOLED PARSING BENCHMARKS (Garbage-Free with JsonParseContext) =====
    
    @Benchmark
    public JsonValue parseFromBufferPooled() {
        // Return any previous views to the pool, then parse with reused context
        pooledContext.close();
        return pooledContext.parse(testBuffer);
    }
    
    @Benchmark
    public JsonValue parseFromStringPooled() {
        pooledContext.close();
        return pooledContext.parse(testString);
    }
    
    @Benchmark
    public JsonValue parseFromByteArrayPooled() {
        pooledContext.close();
        return pooledContext.parse(testBytes);
    }
    
    // ===== NUMBER CACHING THROUGHPUT BENCHMARKS =====
    
    @Benchmark
    public long zeroparseNumberParsingThroughput() {
        JsonValue root = parser.parse(testBuffer);
        if (root.isObject()) {
            JsonValue data = root.asObject().get("data");
            if (data != null && data.isArray() && data.asArray().size() > 0) {
                JsonValue item = data.asArray().get(0);
                if (item != null && item.isObject()) {
                    JsonValue ts = item.asObject().get("ts");
                    if (ts != null && ts.isNumber()) {
                        // Parse number (tests caching on repeated benchmark runs)
                        return ts.asNumber().asLong();
                    }
                }
            }
        }
        return 0;
    }
    
    @Benchmark
    public long fastjson2NumberParsingThroughput() {
        JSONObject obj = JSON.parseObject(testBytes);
        com.alibaba.fastjson2.JSONArray data = obj.getJSONArray("data");
        if (data != null && data.size() > 0) {
            JSONObject item = data.getJSONObject(0);
            return item.getLongValue("ts");
        }
        return 0;
    }

    @Benchmark
    public long lazyjsonNumberParsingThroughput() {
        LazyObject obj = new LazyObject(testString);
        return obj.getLong("ts");
    }
    
    @Benchmark
    public long zeroparsePooledNumberParsingThroughput() {
        pooledContext.close();
        JsonValue root = pooledContext.parse(testBuffer);
        if (root.isObject()) {
            JsonValue data = root.asObject().get("data");
            if (data != null && data.isArray() && data.asArray().size() > 0) {
                JsonValue item = data.asArray().get(0);
                if (item != null && item.isObject()) {
                    JsonValue ts = item.asObject().get("ts");
                    if (ts != null && ts.isNumber()) {
                        return ts.asNumber().asLong();
                    }
                }
            }
        }
        return 0;
    }
    
    @Benchmark
    public double zeroparseQuotedNumberThroughput() {
        JsonValue root = parser.parse(testBuffer);
        if (root.isObject()) {
            JsonArray data = root.asObject().get("data").asArray();
            return data.getObject(0).get("asks").asArray().get(0).asArray().get(0).asString().parseDouble();
        }
        return 0.0;
    }
    
    @Benchmark
    public double fastjson2QuotedNumberThroughput() {
        JSONObject obj = JSON.parseObject(testBytes);
        com.alibaba.fastjson2.JSONArray data = obj.getJSONArray("data");
        if (data != null && data.size() > 0) {
            JSONObject item = data.getJSONObject(0);
            com.alibaba.fastjson2.JSONArray asks = item.getJSONArray("asks");
            if (asks != null && asks.size() > 0) {
                com.alibaba.fastjson2.JSONArray firstAsk = asks.getJSONArray(0);
                // Parse quoted price
                return Double.parseDouble(firstAsk.getString(0));
            }
        }
        return 0.0;
    }

    @Benchmark
    public double lazyjsonQuotedNumberThroughput() {
        LazyObject obj = new LazyObject(testString);
        return obj.getJSONArray("data").getJSONObject(0).getJSONArray("asks").getJSONArray(0).getDouble(0);
    }
    
    @Benchmark
    public double zeroparsePooledQuotedNumberThroughput() {
        pooledContext.close();
        JsonValue root = pooledContext.parse(testBuffer);
        if (root.isObject()) {
            JsonArray data = root.asObject().get("data").asArray();
            return data.getObject(0).get("asks").asArray().get(0).asArray().get(0).asString().parseDouble();
        }
        return 0.0;
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ZeroParseBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}
