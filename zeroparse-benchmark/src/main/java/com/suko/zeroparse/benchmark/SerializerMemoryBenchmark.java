package com.suko.zeroparse.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.suko.zeroparse.JsonSerializeContext;
import com.suko.zeroparse.JsonWriter;
import com.suko.zeroparse.JsonBuilder;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH memory allocation benchmarks for ZeroParse serializer vs FastJson2.
 * 
 * Measures bytes allocated per operation using the GC profiler.
 * 
 * Run with: 
 *   mvn clean package && java -jar target/benchmarks.jar SerializerMemoryBenchmark -prof gc
 * 
 * Or use the main method which includes the GC profiler automatically.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SerializerMemoryBenchmark {
    
    // Test data
    private static final String SYMBOL = "BTCUSDT";
    private static final double PRICE = 27000.50;
    private static final double QUANTITY = 1.5;
    private static final long TIMESTAMP = 1700000000000L;
    private static final int ORDER_ID = 12345;
    
    // ZeroParse reusable objects
    private JsonSerializeContext ctx;
    private JsonWriter reusableWriter;
    private byte[] externalBuffer;
    private ByteBuffer byteBuffer;
    
    // FastJson2 comparison object
    private Map<String, Object> fastJsonObject;
    
    @Setup(Level.Trial)
    public void setup() {
        // ZeroParse setup - create ONCE and reuse
        ctx = new JsonSerializeContext(512);
        reusableWriter = new JsonWriter(512);
        externalBuffer = new byte[512];
        byteBuffer = ByteBuffer.allocate(512);
        
        // FastJson2 setup
        fastJsonObject = new HashMap<>();
        fastJsonObject.put("symbol", SYMBOL);
        fastJsonObject.put("price", PRICE);
        fastJsonObject.put("quantity", QUANTITY);
        fastJsonObject.put("timestamp", TIMESTAMP);
        fastJsonObject.put("orderId", ORDER_ID);
        fastJsonObject.put("side", "BUY");
        fastJsonObject.put("type", "LIMIT");
        fastJsonObject.put("status", "NEW");
    }
    
    // ========== ZeroParse Zero-Allocation Benchmarks ==========
    
    /**
     * Best case: Reuse writer with external buffer.
     * Expected allocation: 0 bytes (truly zero-allocation)
     */
    @Benchmark
    public int zeroparse_reusable_external_buffer(Blackhole bh) {
        reusableWriter.reset(externalBuffer);
        reusableWriter.objectStart();
        reusableWriter.field("symbol", SYMBOL);
        reusableWriter.field("price", PRICE);
        reusableWriter.field("quantity", QUANTITY);
        reusableWriter.field("timestamp", TIMESTAMP);
        reusableWriter.field("orderId", ORDER_ID);
        reusableWriter.field("side", "BUY");
        reusableWriter.field("type", "LIMIT");
        reusableWriter.field("status", "NEW");
        reusableWriter.objectEnd();
        bh.consume(externalBuffer);
        return reusableWriter.size();
    }
    
    /**
     * Context with internal buffer reuse.
     * Expected allocation: 0 bytes when reusing context
     */
    @Benchmark
    public int zeroparse_context_reuse(Blackhole bh) {
        ctx.reset();
        ctx.objectStart()
           .field("symbol", SYMBOL)
           .field("price", PRICE)
           .field("quantity", QUANTITY)
           .field("timestamp", TIMESTAMP)
           .field("orderId", ORDER_ID)
           .field("side", "BUY")
           .field("type", "LIMIT")
           .field("status", "NEW")
           .objectEnd();
        bh.consume(ctx.getInternalBuffer());
        return ctx.size();
    }
    
    /**
     * Context with external ByteBuffer.
     * Expected allocation: 0 bytes
     */
    @Benchmark
    public int zeroparse_context_bytebuffer(Blackhole bh) {
        byteBuffer.clear();
        ctx.reset(byteBuffer);
        ctx.objectStart()
           .field("symbol", SYMBOL)
           .field("price", PRICE)
           .field("quantity", QUANTITY)
           .field("timestamp", TIMESTAMP)
           .field("orderId", ORDER_ID)
           .field("side", "BUY")
           .field("type", "LIMIT")
           .field("status", "NEW")
           .objectEnd();
        bh.consume(byteBuffer);
        return ctx.size();
    }
    
    /**
     * Writer with internal buffer, calling toBytes().
     * Expected allocation: only the result byte[] array
     */
    @Benchmark
    public byte[] zeroparse_reusable_toBytes() {
        reusableWriter.reset();
        reusableWriter.objectStart();
        reusableWriter.field("symbol", SYMBOL);
        reusableWriter.field("price", PRICE);
        reusableWriter.field("quantity", QUANTITY);
        reusableWriter.field("timestamp", TIMESTAMP);
        reusableWriter.field("orderId", ORDER_ID);
        reusableWriter.field("side", "BUY");
        reusableWriter.field("type", "LIMIT");
        reusableWriter.field("status", "NEW");
        reusableWriter.objectEnd();
        return reusableWriter.toBytes();  // This allocates!
    }
    
    /**
     * New writer per operation (worst case for ZeroParse).
     * Expected allocation: JsonWriter + internal buffer
     */
    @Benchmark
    public byte[] zeroparse_new_writer_each_time() {
        JsonWriter writer = new JsonWriter();  // Allocates!
        writer.objectStart();
        writer.field("symbol", SYMBOL);
        writer.field("price", PRICE);
        writer.field("quantity", QUANTITY);
        writer.field("timestamp", TIMESTAMP);
        writer.field("orderId", ORDER_ID);
        writer.field("side", "BUY");
        writer.field("type", "LIMIT");
        writer.field("status", "NEW");
        writer.objectEnd();
        return writer.toBytes();  // Allocates!
    }
    
    /**
     * Builder API (creates new builder each time).
     * Shows allocation cost of the fluent API.
     */
    @Benchmark
    public byte[] zeroparse_builder_new() {
        return JsonBuilder.object()
            .field("symbol", SYMBOL)
            .field("price", PRICE)
            .field("quantity", QUANTITY)
            .field("timestamp", TIMESTAMP)
            .field("orderId", ORDER_ID)
            .field("side", "BUY")
            .field("type", "LIMIT")
            .field("status", "NEW")
            .end()
            .toBytes();
    }
    
    // ========== FastJson2 Benchmarks ==========
    
    /**
     * FastJson2 object serialization.
     */
    @Benchmark
    public byte[] fastjson2_toBytes() {
        return JSON.toJSONBytes(fastJsonObject);
    }
    
    /**
     * FastJson2 to String (then would need getBytes).
     */
    @Benchmark
    public String fastjson2_toString() {
        return JSON.toJSONString(fastJsonObject);
    }
    
    /**
     * FastJson2 JSONWriter API.
     */
    @Benchmark
    public byte[] fastjson2_writer() {
        try (JSONWriter jsonWriter = JSONWriter.of()) {
            jsonWriter.startObject();
            jsonWriter.writeName("symbol");
            jsonWriter.writeString(SYMBOL);
            jsonWriter.writeName("price");
            jsonWriter.writeDouble(PRICE);
            jsonWriter.writeName("quantity");
            jsonWriter.writeDouble(QUANTITY);
            jsonWriter.writeName("timestamp");
            jsonWriter.writeInt64(TIMESTAMP);
            jsonWriter.writeName("orderId");
            jsonWriter.writeInt32(ORDER_ID);
            jsonWriter.writeName("side");
            jsonWriter.writeString("BUY");
            jsonWriter.writeName("type");
            jsonWriter.writeString("LIMIT");
            jsonWriter.writeName("status");
            jsonWriter.writeString("NEW");
            jsonWriter.endObject();
            return jsonWriter.getBytes();
        }
    }
    
    // ========== Complex Structure Benchmarks ==========
    
    /**
     * Orderbook with reusable writer and external buffer.
     * Expected: 0 allocation
     */
    @Benchmark
    public int zeroparse_orderbook_zero_alloc(Blackhole bh) {
        reusableWriter.reset(externalBuffer);
        reusableWriter.objectStart();
        reusableWriter.field("symbol", SYMBOL);
        reusableWriter.field("timestamp", TIMESTAMP);
        
        reusableWriter.fieldName("bids");
        reusableWriter.arrayStart();
        for (int i = 0; i < 10; i++) {
            reusableWriter.arrayStart();
            reusableWriter.writeDouble(27000.0 - i);
            reusableWriter.writeDouble(1.0 + i * 0.1);
            reusableWriter.arrayEnd();
        }
        reusableWriter.arrayEnd();
        
        reusableWriter.fieldName("asks");
        reusableWriter.arrayStart();
        for (int i = 0; i < 10; i++) {
            reusableWriter.arrayStart();
            reusableWriter.writeDouble(27001.0 + i);
            reusableWriter.writeDouble(1.0 + i * 0.1);
            reusableWriter.arrayEnd();
        }
        reusableWriter.arrayEnd();
        
        reusableWriter.objectEnd();
        bh.consume(externalBuffer);
        return reusableWriter.size();
    }
    
    /**
     * Orderbook with toBytes() allocation.
     */
    @Benchmark
    public byte[] zeroparse_orderbook_toBytes() {
        reusableWriter.reset();
        reusableWriter.objectStart();
        reusableWriter.field("symbol", SYMBOL);
        reusableWriter.field("timestamp", TIMESTAMP);
        
        reusableWriter.fieldName("bids");
        reusableWriter.arrayStart();
        for (int i = 0; i < 10; i++) {
            reusableWriter.arrayStart();
            reusableWriter.writeDouble(27000.0 - i);
            reusableWriter.writeDouble(1.0 + i * 0.1);
            reusableWriter.arrayEnd();
        }
        reusableWriter.arrayEnd();
        
        reusableWriter.fieldName("asks");
        reusableWriter.arrayStart();
        for (int i = 0; i < 10; i++) {
            reusableWriter.arrayStart();
            reusableWriter.writeDouble(27001.0 + i);
            reusableWriter.writeDouble(1.0 + i * 0.1);
            reusableWriter.arrayEnd();
        }
        reusableWriter.arrayEnd();
        
        reusableWriter.objectEnd();
        return reusableWriter.toBytes();
    }
    
    /**
     * FastJson2 orderbook for comparison.
     */
    @Benchmark
    public byte[] fastjson2_orderbook() {
        try (JSONWriter jsonWriter = JSONWriter.of()) {
            jsonWriter.startObject();
            jsonWriter.writeName("symbol");
            jsonWriter.writeString(SYMBOL);
            jsonWriter.writeName("timestamp");
            jsonWriter.writeInt64(TIMESTAMP);
            
            jsonWriter.writeName("bids");
            jsonWriter.startArray();
            for (int i = 0; i < 10; i++) {
                jsonWriter.startArray();
                jsonWriter.writeDouble(27000.0 - i);
                jsonWriter.writeDouble(1.0 + i * 0.1);
                jsonWriter.endArray();
            }
            jsonWriter.endArray();
            
            jsonWriter.writeName("asks");
            jsonWriter.startArray();
            for (int i = 0; i < 10; i++) {
                jsonWriter.startArray();
                jsonWriter.writeDouble(27001.0 + i);
                jsonWriter.writeDouble(1.0 + i * 0.1);
                jsonWriter.endArray();
            }
            jsonWriter.endArray();
            
            jsonWriter.endObject();
            return jsonWriter.getBytes();
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializerMemoryBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)  // Add GC profiler for allocation stats
                .build();
        
        new Runner(opt).run();
    }
}

