package com.suko.zeroparse.benchmark.profiler;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.TextResult;

import com.jprofiler.api.controller.Controller;

import java.io.File;
import java.util.*;

/**
 * Custom JMH profiler that integrates JProfiler for offline CPU and memory profiling.
 * 
 * This profiler automatically starts/stops JProfiler recordings during benchmark iterations
 * and saves snapshots to disk for later analysis.
 * 
 * Usage:
 * 1. Ensure JProfiler agent is loaded via -agentpath JVM argument
 * 2. Add profiler to JMH options: .addProfiler(JProfilerOfflineProfiler.class)
 * 3. Configure via system properties:
 *    - jprofiler.output.dir: Output directory for snapshots (default: ./jprofiler-snapshots)
 *    - jprofiler.cpu.enabled: Enable CPU profiling (default: true)
 *    - jprofiler.memory.enabled: Enable memory profiling (default: true)
 *    - jprofiler.probe.enabled: Enable probe recording (default: false)
 * 
 * Example:
 * <pre>
 * Options opt = new OptionsBuilder()
 *     .include(MyBenchmark.class.getSimpleName())
 *     .addProfiler(JProfilerOfflineProfiler.class)
 *     .jvmArgs("-agentpath:/path/to/jprofiler/bin/linux-x64/libjprofilerti.so=offline,id=123,config=/path/to/config.xml")
 *     .build();
 * </pre>
 */
public class JProfilerOfflineProfiler implements InternalProfiler {
    
    private static final String OUTPUT_DIR_PROPERTY = "jprofiler.output.dir";
    private static final String CPU_ENABLED_PROPERTY = "jprofiler.cpu.enabled";
    private static final String MEMORY_ENABLED_PROPERTY = "jprofiler.memory.enabled";
    
    private static final String DEFAULT_OUTPUT_DIR = "./jprofiler-snapshots";
    
    private File outputDir;
    private boolean cpuEnabled;
    private boolean memoryEnabled;
    private boolean jprofilerAvailable;
    
    private String currentBenchmarkName;
    private int iterationCount;
    
    public JProfilerOfflineProfiler() {
        // Configuration
        String outputDirPath = System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIR);
        this.outputDir = new File(outputDirPath);
        this.cpuEnabled = Boolean.parseBoolean(System.getProperty(CPU_ENABLED_PROPERTY, "true"));
        this.memoryEnabled = Boolean.parseBoolean(System.getProperty(MEMORY_ENABLED_PROPERTY, "true"));
        
        // Initialize JProfiler controller via reflection (to avoid hard dependency)
        initializeJProfilerController();
        
        // Create output directory
        if (jprofilerAvailable && !outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        iterationCount = 0;
    }
    
    private void initializeJProfilerController() {
        jprofilerAvailable = true;
        System.out.println("[JProfilerOfflineProfiler] JProfiler controller initialized successfully");
        System.out.println("[JProfilerOfflineProfiler] Output directory: " + outputDir.getAbsolutePath());
        System.out.println("[JProfilerOfflineProfiler] CPU profiling: " + cpuEnabled);
        System.out.println("[JProfilerOfflineProfiler] Memory profiling: " + memoryEnabled);
    }
    
    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        if (!jprofilerAvailable) {
            return;
        }
        
        currentBenchmarkName = benchmarkParams.getBenchmark();
        iterationCount++;
        
        // Only profile measurement iterations (not warmup)
        if (!iterationParams.getType().toString().equals("MEASUREMENT")) {
            return;
        }
        
        try {
            System.out.println("[JProfilerOfflineProfiler] Starting profiling for: " + 
                    currentBenchmarkName + " iteration " + iterationCount);
            
            // Start CPU profiling
            if (cpuEnabled) {
                Controller.startCPURecording(true);
            }

            if(memoryEnabled) {
                Controller.startAllocRecording(true);
            }
            
        } catch (Exception e) {
            System.err.println("[JProfilerOfflineProfiler] Error starting profiling: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public Collection<? extends Result<?>> afterIteration(BenchmarkParams benchmarkParams, 
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        if (!jprofilerAvailable) {
            return Collections.emptyList();
        }
        
        // Only profile measurement iterations
        if (!iterationParams.getType().toString().equals("MEASUREMENT")) {
            return Collections.emptyList();
        }
        
        try {

            // Trigger heap dump if memory profiling is enabled
            if (memoryEnabled) {
                System.gc();
                try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
                Controller.stopAllocRecording();
                System.out.println("[JProfilerOfflineProfiler] Allocation recording stopped");
            }

            // Stop CPU profiling
            if (cpuEnabled) {
                Controller.stopCPURecording();
                System.out.println("[JProfilerOfflineProfiler] CPU recording stopped");
            }
            
            // Save snapshot
            String sanitizedBenchmarkName = sanitizeBenchmarkName(currentBenchmarkName);
            String timestamp = String.format("%tY%<tm%<td_%<tH%<tM%<tS", System.currentTimeMillis());
            String fileName = String.format("%s_iter%d_%s.jps", 
                    sanitizedBenchmarkName, iterationCount, timestamp);
            File snapshotFile = new File(outputDir, fileName);
            
            Controller.saveSnapshot(snapshotFile);
            
            System.out.println("[JProfilerOfflineProfiler] Snapshot saved: " + snapshotFile.getAbsolutePath());
            
            // Return result with snapshot location
            return Collections.singleton(new TextResult(
                    snapshotFile.getAbsolutePath(),
                    "Snapshot location"
            ));
            
        } catch (Exception e) {
            System.err.println("[JProfilerOfflineProfiler] Error stopping profiling: " + e.getMessage());
            e.printStackTrace();
        }
        
        return Collections.emptyList();
    }
    
    private String sanitizeBenchmarkName(String benchmarkName) {
        // Remove package name, keep only class and method
        String simplified = benchmarkName;
        int lastDot = simplified.lastIndexOf('.');
        if (lastDot != -1) {
            simplified = simplified.substring(lastDot + 1);
        }
        // Replace invalid file name characters
        return simplified.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    
    @Override
    public String getDescription() {
        return "JProfiler offline profiler - captures CPU, memory, and probe data during benchmark iterations";
    }
    
    /**
     * Builder class for easier configuration in code
     */
    public static class Builder {
        private String outputDir = DEFAULT_OUTPUT_DIR;
        private boolean cpuEnabled = false;
        private boolean memoryEnabled = false;
        
        public Builder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }
        
        public Builder enableCpu(boolean enabled) {
            this.cpuEnabled = enabled;
            return this;
        }
        
        public Builder enableMemory(boolean enabled) {
            this.memoryEnabled = enabled;
            return this;
        }
        
        public void configure() {
            System.setProperty(OUTPUT_DIR_PROPERTY, outputDir);
            System.setProperty(CPU_ENABLED_PROPERTY, String.valueOf(cpuEnabled));
            System.setProperty(MEMORY_ENABLED_PROPERTY, String.valueOf(memoryEnabled));
        }
    }
}

