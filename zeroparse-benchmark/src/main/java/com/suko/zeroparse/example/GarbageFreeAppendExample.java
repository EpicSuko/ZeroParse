package com.suko.zeroparse.example;

import com.suko.zeroparse.*;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;

/**
 * Example demonstrating garbage-free string appending using JsonStringView.appendTo().
 * 
 * These methods are designed for high-performance scenarios where minimizing
 * garbage collection is critical, such as:
 * - Building WebSocket response messages
 * - Streaming JSON data transformation
 * - High-frequency logging or monitoring
 */
public class GarbageFreeAppendExample {
    
    public static void main(String[] args) throws IOException {
        // Example JSON with various string types
        String json = "{" +
            "\"symbol\":\"BTCUSDT\"," +
            "\"message\":\"Trade executed\\nPrice: $27000.50\\tQuantity: 0.125\"," +
            "\"unicode\":\"Bitcoin \\u20BF to USD \\u0024\"," +
            "\"simple\":\"No escapes here\"" +
            "}";
        
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(json).asObject();
        
        // Example 1: Building a message using StringBuilder (Appendable)
        StringBuilder message = new StringBuilder();
        message.append("Symbol: ");
        obj.get("symbol").asString().appendTo(message);
        message.append(" - ");
        obj.get("message").asString().appendTo(message);
        
        System.out.println("Built message:");
        System.out.println(message);
        
        // Example 2: Building a Vert.x Buffer for network transmission
        Buffer responseBuffer = Buffer.buffer();
        responseBuffer.appendString("{\"response\":\"");
        obj.get("unicode").asString().appendTo(responseBuffer);
        responseBuffer.appendString("\",\"timestamp\":");
        responseBuffer.appendString(String.valueOf(System.currentTimeMillis()));
        responseBuffer.appendString("}");
        
        System.out.println("\nBuilt buffer:");
        System.out.println(responseBuffer.toString());
        
        // Example 3: Performance comparison - building many messages
        System.out.println("\nPerformance comparison:");
        int iterations = 1_000_000;
        
        // Traditional approach (creates many String objects)
        long startTraditional = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String symbol = obj.get("symbol").toString();
            String msg = obj.get("simple").toString();
            String result = "Trade: " + symbol + " - " + msg;
        }
        long endTraditional = System.nanoTime();
        
        // Garbage-free approach
        StringBuilder reusableBuilder = new StringBuilder();
        long startGarbageFree = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            reusableBuilder.setLength(0); // Reset without allocation
            reusableBuilder.append("Trade: ");
            obj.get("symbol").asString().appendTo(reusableBuilder);
            reusableBuilder.append(" - ");
            obj.get("simple").asString().appendTo(reusableBuilder);
            // Result is in reusableBuilder, ready to use
        }
        long endGarbageFree = System.nanoTime();
        
        System.out.printf("Traditional approach: %.2f ms%n", 
            (endTraditional - startTraditional) / 1_000_000.0);
        System.out.printf("Garbage-free approach: %.2f ms%n", 
            (endGarbageFree - startGarbageFree) / 1_000_000.0);
        System.out.printf("Speedup: %.2fx%n", 
            (double)(endTraditional - startTraditional) / (endGarbageFree - startGarbageFree));
        
        // Example 4: Complex Buffer building scenario
        System.out.println("\nComplex buffer building (simulating WebSocket frame):");
        Buffer wsFrame = Buffer.buffer();
        
        // Build a WebSocket-like frame completely garbage-free
        wsFrame.appendByte((byte) 0x81); // Text frame
        wsFrame.appendByte((byte) 0x7E); // Extended payload length
        
        // Build the payload
        Buffer payload = Buffer.buffer();
        payload.appendString("{\"type\":\"trade\",\"data\":{");
        payload.appendString("\"symbol\":\"");
        obj.get("symbol").asString().appendTo(payload);
        payload.appendString("\",\"info\":\"");
        obj.get("unicode").asString().appendTo(payload); // Unicode handled correctly
        payload.appendString("\"}}");
        
        // Add payload length and payload
        wsFrame.appendUnsignedShort(payload.length());
        wsFrame.appendBuffer(payload);
        
        System.out.println("Frame size: " + wsFrame.length() + " bytes");
        System.out.println("Payload: " + payload.toString());
        
        // Key benefits demonstrated:
        // 1. Zero String allocations when building output
        // 2. Correct handling of escaped characters (\\n, \\t, \\uXXXX)
        // 3. Direct UTF-8 byte handling for Buffer operations
        // 4. Reusable builders for repeated operations
    }
}
