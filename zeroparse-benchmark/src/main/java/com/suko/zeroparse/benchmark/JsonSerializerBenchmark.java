package com.suko.zeroparse.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.suko.zeroparse.JsonSerializeContext;
import com.suko.zeroparse.JsonWriter;
import com.suko.zeroparse.JsonBuilder;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing ZeroParse serializer with FastJson2.
 * 
 * Run with:
 * java -jar target/benchmarks.jar JsonSerializerBenchmark -f 1 -wi 3 -i 5
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonSerializerBenchmark {
    
    // Test data
    private static final String SYMBOL = "BTCUSDT";
    private static final double PRICE = 27000.50;
    private static final double QUANTITY = 1.5;
    private static final long TIMESTAMP = 1700000000000L;
    private static final int ORDER_ID = 12345;
    
    // ZeroParse objects (reusable)
    private JsonSerializeContext ctx;
    private JsonWriter writer;
    private byte[] externalBuffer;
    private ByteBuffer byteBuffer;
    
    // FastJson2 comparison object
    private Map<String, Object> fastJsonObject;
    
    @Setup(Level.Trial)
    public void setup() {
        // ZeroParse setup
        ctx = new JsonSerializeContext(512);
        writer = new JsonWriter(512);
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
    
    // ========== ZeroParse Benchmarks ==========
    
    @Benchmark
    public byte[] zeroparse_writer_simple() {
        writer.reset();
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
        return writer.toBytes();
    }
    
    @Benchmark
    public int zeroparse_writer_external_buffer(Blackhole bh) {
        writer.reset(externalBuffer);
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
        bh.consume(externalBuffer);
        return writer.size();
    }
    
    @Benchmark
    public int zeroparse_context_simple(Blackhole bh) {
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
    
    @Benchmark
    public int zeroparse_context_external(Blackhole bh) {
        ctx.reset(externalBuffer);
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
        bh.consume(externalBuffer);
        return ctx.size();
    }
    
    @Benchmark
    public int zeroparse_bytebuffer(Blackhole bh) {
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
    
    @Benchmark
    public byte[] zeroparse_builder() {
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
    
    @Benchmark
    public byte[] fastjson2_toBytes() {
        return JSON.toJSONBytes(fastJsonObject);
    }
    
    @Benchmark
    public String fastjson2_toString() {
        return JSON.toJSONString(fastJsonObject);
    }
    
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
    
    @Benchmark
    public byte[] zeroparse_orderbook() {
        writer.reset();
        writer.objectStart();
        writer.field("symbol", SYMBOL);
        writer.field("timestamp", TIMESTAMP);
        
        writer.fieldName("bids");
        writer.arrayStart();
        for (int i = 0; i < 10; i++) {
            writer.arrayStart();
            writer.writeDouble(27000.0 - i);
            writer.writeDouble(1.0 + i * 0.1);
            writer.arrayEnd();
        }
        writer.arrayEnd();
        
        writer.fieldName("asks");
        writer.arrayStart();
        for (int i = 0; i < 10; i++) {
            writer.arrayStart();
            writer.writeDouble(27001.0 + i);
            writer.writeDouble(1.0 + i * 0.1);
            writer.arrayEnd();
        }
        writer.arrayEnd();
        
        writer.objectEnd();
        return writer.toBytes();
    }
    
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
                .include(JsonSerializerBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}

