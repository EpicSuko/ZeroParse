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
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ZeroParseBenchmark {
    
    private Buffer testBuffer;
    private String testString;
    private byte[] testBytes;
    private JsonParser parser;
    
    @Setup
    public void setup() {
        // Create test JSON data
        String testJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        testBuffer = Buffer.buffer(testJson);
        testString = testBuffer.toString();
        testBytes = testBuffer.getBytes();
        
        // Create parser ONCE in setup
        parser = new JsonParser();
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
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ZeroParseBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}
