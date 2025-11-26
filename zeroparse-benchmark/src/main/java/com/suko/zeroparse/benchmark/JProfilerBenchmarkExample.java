package com.suko.zeroparse.benchmark;

import com.suko.zeroparse.JsonParser;
import com.suko.zeroparse.JsonParseContext;
import com.suko.zeroparse.JsonValue;
import com.suko.zeroparse.benchmark.profiler.JProfilerOfflineProfiler;
import io.vertx.core.buffer.Buffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Example benchmark demonstrating JProfiler offline profiling integration.
 * 
 * To run with JProfiler profiling:
 * 
 * 1. Build the project:
 *    mvn clean package
 * 
 * 2. Run with JProfiler agent:
 *    java -agentpath:/path/to/jprofiler/bin/[os]/libjprofilerti.so=offline,id=123 \
 *         -Djprofiler.output.dir=./jprofiler-snapshots \
 *         -Djprofiler.cpu.enabled=true \
 *         -Djprofiler.memory.enabled=true \
 *         -jar target/benchmarks.jar JProfilerBenchmarkExample
 * 
 * Windows example:
 *    java -agentpath:"C:\Program Files\JProfiler\bin\windows-x64\jprofilerti.dll"=offline,id=123 ^
 *         -Djprofiler.output.dir=./jprofiler-snapshots ^
 *         -jar target/benchmarks.jar JProfilerBenchmarkExample
 * 
 * macOS example:
 *    java -agentpath:/Applications/JProfiler.app/Contents/Resources/app/bin/macos/libjprofilerti.jnilib=offline,id=123 \
 *         -Djprofiler.output.dir=./jprofiler-snapshots \
 *         -jar target/benchmarks.jar JProfilerBenchmarkExample
 * 
 * The profiler will create .jps snapshot files in the output directory that can be
 * opened in JProfiler GUI for detailed analysis.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JProfilerBenchmarkExample {
    
    private Buffer priceBuffer;
    private JsonParser parser;
    private JsonParseContext pooledContext;
    
    @Setup
    public void setup() {
        // Realistic crypto orderbook JSON
        String testJson = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"books5\",\"instId\":\"BTCUSDT\"},\"data\":[{\"asks\":[[\"27000.5\",\"8.760\"],[\"27001.0\",\"0.400\"],[\"27001.5\",\"1.200\"],[\"27002.0\",\"0.850\"],[\"27002.5\",\"2.100\"]],\"bids\":[[\"27000.0\",\"2.710\"],[\"26999.5\",\"1.460\"],[\"26999.0\",\"0.920\"],[\"26998.5\",\"1.580\"],[\"26998.0\",\"0.650\"]],\"checksum\":0,\"seq\":123,\"ts\":\"1695716059516\"}],\"ts\":1695716059516}";
        parser = new JsonParser();
        String json = "{\"symbol\":\"BTCUSDT\",\"exchange\":\"binance\",\"type\":\"limit\",\"side\":\"buy\",\"price\":\"27000.50\",\"quantity\":\"1.23456789\"}";
        String json2 = "{\"price\":\"27000.50\"}";
        priceBuffer = Buffer.buffer(json2);
        pooledContext = new JsonParseContext(parser, new com.suko.zeroparse.ViewPools());
    }

    @Benchmark
    public JsonValue parsePriceObjectNewPool(Blackhole bh) {
        // Reuse a single context so JProfiler shows steady-state pooling behavior
        pooledContext.close();
        return pooledContext.parse(priceBuffer);
    }
    
    // @Benchmark
    // public void parseAndAccessFields(Blackhole bh) {
    //     JsonValue root = parser.parse(testBuffer);
    //     if (root.isObject()) {
    //         JsonObject obj = root.asObject();
    //         bh.consume(obj.get("action"));
    //         JsonValue data = obj.get("data");
            
    //         if (data != null && data.isArray()) {
    //             JsonValue firstItem = data.asArray().get(0);
    //             if (firstItem != null && firstItem.isObject()) {
    //                 JsonObject item = firstItem.asObject();
    //                 JsonValue asks = item.get("asks");
    //                 bh.consume(item.get("bids"));
                    
    //                 // Parse prices
    //                 if (asks != null && asks.isArray() && asks.asArray().size() > 0) {
    //                     JsonValue firstAsk = asks.asArray().get(0);
    //                     if (firstAsk != null && firstAsk.isArray()) {
    //                         bh.consume(firstAsk.asArray().get(0).asString().parseDouble());
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }
    
    // @Benchmark
    // public void pooledParseAndAccessFields(Blackhole bh) {
    //     try (JsonParseContext ctx = JsonParseContext.get()) {
    //         JsonValue root = ctx.parse(testBuffer);
    //         if (root.isObject()) {
    //             JsonObject obj = root.asObject();
    //             bh.consume(obj.get("action"));
    //             JsonValue data = obj.get("data");
                
    //             if (data != null && data.isArray()) {
    //                 JsonValue firstItem = data.asArray().get(0);
    //                 if (firstItem != null && firstItem.isObject()) {
    //                     JsonObject item = firstItem.asObject();
    //                     JsonValue asks = item.get("asks");
    //                     bh.consume(item.get("bids"));
                        
    //                     // Parse prices
    //                     if (asks != null && asks.isArray() && asks.asArray().size() > 0) {
    //                         JsonValue firstAsk = asks.asArray().get(0);
    //                         if (firstAsk != null && firstAsk.isArray()) {
    //                             bh.consume(firstAsk.asArray().get(0).asString().parseDouble());
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }

    // @Benchmark
    // public void profileStringMaterialization(Blackhole bh) {
    //     try (JsonParseContext ctx = JsonParseContext.get()) {
    //         JsonValue root = ctx.parse(stringBuffer);
    //         JsonObject obj = root.asObject();
    //         // Materialize all strings (calls toString())
    //         // Note: String allocations will be same as non-pooled
    //         bh.consume(obj.get("symbol").asString().toString());
    //         bh.consume(obj.get("exchange").asString().toString());
    //         bh.consume(obj.get("type").asString().toString());
    //         bh.consume(obj.get("side").asString().toString());
    //         bh.consume(obj.get("price").asString().toString());
    //         bh.consume(obj.get("quantity").asString().toString());
    //     }
    // }
    
    public static void main(String[] args) throws RunnerException {
        // Configure JProfiler profiler programmatically
        new JProfilerOfflineProfiler.Builder()
                .outputDir("./jprofiler-snapshots")
                .enableCpu(true)
                .enableMemory(true)
                .configure();
        
        Options opt = new OptionsBuilder()
                .include(JProfilerBenchmarkExample.class.getSimpleName())
                // .addProfiler(GCProfiler.class)
                .addProfiler(JProfilerOfflineProfiler.class)
                .jvmArgs("-agentpath:D:\\jprofiler15\\bin\\windows-x64\\jprofilerti.dll=offline,id=110,config=C:\\Users\\Steven\\.jprofiler15\\jprofiler_config.xml")
                .build();
        
        new Runner(opt).run();
    }
}

