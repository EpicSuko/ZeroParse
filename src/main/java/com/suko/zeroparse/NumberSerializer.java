package com.suko.zeroparse;

/**
 * Zero-allocation number serializer that writes directly to byte arrays.
 * 
 * <p>This serializer eliminates the need to create intermediate String objects
 * when writing JSON numbers, significantly reducing GC pressure in
 * high-throughput scenarios.</p>
 * 
 * <p>Uses pre-computed digit tables for fast int/long serialization and
 * optimized formatting for common exchange price formats.</p>
 */
public final class NumberSerializer {
    
    // Pre-computed two-digit lookup table (00-99)
    // Each pair of bytes represents a two-digit number
    private static final byte[] DIGIT_PAIRS = new byte[200];
    
    // Single digit lookup (0-9)
    private static final byte[] DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };
    
    // Powers of 10 for double formatting
    private static final long[] POWERS_OF_10 = {
        1L,
        10L,
        100L,
        1000L,
        10000L,
        100000L,
        1000000L,
        10000000L,
        100000000L,
        1000000000L,
        10000000000L,
        100000000000L,
        1000000000000L,
        10000000000000L,
        100000000000000L,
        1000000000000000L,
        10000000000000000L,
        100000000000000000L,
        1000000000000000000L
    };
    
    // Maximum digits for long (19 digits + sign)
    private static final int MAX_LONG_DIGITS = 20;
    
    // Maximum digits for double (sign + digits + decimal + fraction + exponent)
    private static final int MAX_DOUBLE_DIGITS = 32;
    
    static {
        // Initialize digit pairs table
        for (int i = 0; i < 100; i++) {
            DIGIT_PAIRS[i * 2] = (byte) ('0' + i / 10);
            DIGIT_PAIRS[i * 2 + 1] = (byte) ('0' + i % 10);
        }
    }
    
    private NumberSerializer() {
        // Utility class
    }
    
    // ========== Zero-Allocation Direct Methods (using recursion) ==========
    
    /**
     * Write an int directly to the output cursor with zero allocation.
     * Uses recursion to write digits in correct order.
     * 
     * @param value the int value to write
     * @param output the output cursor
     */
    public static void writeIntDirect(int value, OutputCursor output) {
        if (value == Integer.MIN_VALUE) {
            output.writeBytes(MIN_INT_BYTES, 0, MIN_INT_BYTES.length);
            return;
        }
        
        if (value < 0) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        writePositiveIntRecursive(value, output);
    }
    
    /**
     * Write a long directly to the output cursor with zero allocation.
     * Uses recursion to write digits in correct order.
     * 
     * @param value the long value to write
     * @param output the output cursor
     */
    public static void writeLongDirect(long value, OutputCursor output) {
        if (value == Long.MIN_VALUE) {
            output.writeBytes(MIN_LONG_BYTES, 0, MIN_LONG_BYTES.length);
            return;
        }
        
        if (value < 0) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        writePositiveLongRecursive(value, output);
    }
    
    /**
     * Write a double directly to the output cursor with zero allocation.
     * Uses recursion for integer/fractional parts.
     * 
     * @param value the double value to write
     * @param output the output cursor
     */
    public static void writeDoubleDirect(double value, OutputCursor output) {
        // Handle special cases
        if (Double.isNaN(value)) {
            output.writeBytes(NAN_BYTES, 0, NAN_BYTES.length);
            return;
        }
        if (Double.isInfinite(value)) {
            if (value < 0) {
                output.writeBytes(NEG_INF_BYTES, 0, NEG_INF_BYTES.length);
            } else {
                output.writeBytes(POS_INF_BYTES, 0, POS_INF_BYTES.length);
            }
            return;
        }
        if (value == 0.0) {
            if (Double.doubleToRawLongBits(value) == 0x8000000000000000L) {
                output.writeBytes(NEG_ZERO_BYTES, 0, NEG_ZERO_BYTES.length);
            } else {
                output.writeBytes(ZERO_BYTES, 0, ZERO_BYTES.length);
            }
            return;
        }
        
        if (value < 0) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        // Try optimized path for common exchange prices
        if (value < 1e15 && value >= 0.0001) {
            if (writeDoubleOptimizedDirect(value, output)) {
                return;
            }
        }
        
        // Fallback (allocates String)
        writeDoubleFallback(value, output);
    }
    
    /**
     * Write positive int recursively (zero allocation).
     */
    private static void writePositiveIntRecursive(int value, OutputCursor output) {
        if (value >= 10) {
            writePositiveIntRecursive(value / 10, output);
        }
        output.writeByte(DIGITS[value % 10]);
    }
    
    /**
     * Write positive long recursively (zero allocation).
     */
    private static void writePositiveLongRecursive(long value, OutputCursor output) {
        if (value >= 10) {
            writePositiveLongRecursive(value / 10, output);
        }
        output.writeByte(DIGITS[(int) (value % 10)]);
    }
    
    /**
     * Optimized double formatting with zero allocation.
     * Returns true if successful.
     */
    private static boolean writeDoubleOptimizedDirect(double value, OutputCursor output) {
        int scale = 8;
        long multiplier = POWERS_OF_10[scale];
        long scaled = Math.round(value * multiplier);
        
        // Verify precision
        double reconstructed = scaled / (double) multiplier;
        if (Math.abs(reconstructed - value) > value * 1e-10 && Math.abs(reconstructed - value) > 1e-15) {
            return false;
        }
        
        // Remove trailing zeros
        while (scale > 1 && scaled % 10 == 0) {
            scaled /= 10;
            scale--;
        }
        
        long divisor = POWERS_OF_10[scale];
        long intPart = scaled / divisor;
        long fracPart = scaled % divisor;
        
        // Write integer part
        if (intPart == 0) {
            output.writeByte((byte) '0');
        } else {
            writePositiveLongRecursive(intPart, output);
        }
        
        // Write decimal point
        output.writeByte((byte) '.');
        
        // Pad with leading zeros if needed
        int fracDigits = countDigits(fracPart);
        while (fracDigits < scale) {
            output.writeByte((byte) '0');
            scale--;
        }
        
        // Write fractional part
        if (fracPart > 0) {
            writePositiveLongRecursive(fracPart, output);
        }
        
        return true;
    }
    
    // ========== Original Methods (may allocate temp arrays) ==========
    
    /**
     * Write an int to the output cursor.
     * 
     * @param value the int value to write
     * @param output the output cursor
     */
    public static void writeInt(int value, OutputCursor output) {
        if (value == Integer.MIN_VALUE) {
            // Special case: -2147483648 can't be negated
            output.writeBytes(MIN_INT_BYTES, 0, MIN_INT_BYTES.length);
            return;
        }
        
        if (value < 0) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        writePositiveInt(value, output);
    }
    
    /**
     * Write an int to a byte array scratch buffer.
     * Returns the number of bytes written.
     * 
     * @param value the int value to write
     * @param buffer the scratch buffer (must be at least 11 bytes)
     * @return the number of bytes written
     */
    public static int writeInt(int value, byte[] buffer) {
        int pos = 0;
        
        if (value == Integer.MIN_VALUE) {
            System.arraycopy(MIN_INT_BYTES, 0, buffer, 0, MIN_INT_BYTES.length);
            return MIN_INT_BYTES.length;
        }
        
        if (value < 0) {
            buffer[pos++] = '-';
            value = -value;
        }
        
        return pos + writePositiveInt(value, buffer, pos);
    }
    
    /**
     * Write a long to the output cursor.
     * 
     * @param value the long value to write
     * @param output the output cursor
     */
    public static void writeLong(long value, OutputCursor output) {
        if (value == Long.MIN_VALUE) {
            // Special case: -9223372036854775808 can't be negated
            output.writeBytes(MIN_LONG_BYTES, 0, MIN_LONG_BYTES.length);
            return;
        }
        
        if (value < 0) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        writePositiveLong(value, output);
    }
    
    /**
     * Write a long to a byte array scratch buffer.
     * Returns the number of bytes written.
     * 
     * @param value the long value to write
     * @param buffer the scratch buffer (must be at least 20 bytes)
     * @return the number of bytes written
     */
    public static int writeLong(long value, byte[] buffer) {
        int pos = 0;
        
        if (value == Long.MIN_VALUE) {
            System.arraycopy(MIN_LONG_BYTES, 0, buffer, 0, MIN_LONG_BYTES.length);
            return MIN_LONG_BYTES.length;
        }
        
        if (value < 0) {
            buffer[pos++] = '-';
            value = -value;
        }
        
        return pos + writePositiveLong(value, buffer, pos);
    }
    
    /**
     * Write a double to the output cursor.
     * Uses optimized formatting for common exchange price formats.
     * 
     * @param value the double value to write
     * @param output the output cursor
     */
    public static void writeDouble(double value, OutputCursor output) {
        // Handle special cases
        if (Double.isNaN(value)) {
            output.writeBytes(NAN_BYTES, 0, NAN_BYTES.length);
            return;
        }
        if (Double.isInfinite(value)) {
            if (value < 0) {
                output.writeBytes(NEG_INF_BYTES, 0, NEG_INF_BYTES.length);
            } else {
                output.writeBytes(POS_INF_BYTES, 0, POS_INF_BYTES.length);
            }
            return;
        }
        if (value == 0.0) {
            // Check for negative zero
            if (Double.doubleToRawLongBits(value) == 0x8000000000000000L) {
                output.writeBytes(NEG_ZERO_BYTES, 0, NEG_ZERO_BYTES.length);
            } else {
                output.writeBytes(ZERO_BYTES, 0, ZERO_BYTES.length);
            }
            return;
        }
        
        boolean negative = value < 0;
        if (negative) {
            output.writeByte((byte) '-');
            value = -value;
        }
        
        // Try fast path for common exchange price formats (e.g., 27000.50, 1.2345)
        if (value < 1e15 && value >= 0.0001) {
            if (writeDoubleOptimized(value, output)) {
                return;
            }
        }
        
        // Fall back to standard formatting via Double.toString
        writeDoubleFallback(value, output);
    }
    
    /**
     * Write a double to a byte array scratch buffer.
     * Returns the number of bytes written.
     * 
     * @param value the double value to write
     * @param buffer the scratch buffer (must be at least 32 bytes)
     * @return the number of bytes written
     */
    public static int writeDouble(double value, byte[] buffer) {
        int pos = 0;
        
        // Handle special cases
        if (Double.isNaN(value)) {
            System.arraycopy(NAN_BYTES, 0, buffer, 0, NAN_BYTES.length);
            return NAN_BYTES.length;
        }
        if (Double.isInfinite(value)) {
            byte[] bytes = value < 0 ? NEG_INF_BYTES : POS_INF_BYTES;
            System.arraycopy(bytes, 0, buffer, 0, bytes.length);
            return bytes.length;
        }
        if (value == 0.0) {
            byte[] bytes = Double.doubleToRawLongBits(value) == 0x8000000000000000L 
                ? NEG_ZERO_BYTES : ZERO_BYTES;
            System.arraycopy(bytes, 0, buffer, 0, bytes.length);
            return bytes.length;
        }
        
        if (value < 0) {
            buffer[pos++] = '-';
            value = -value;
        }
        
        // Try fast path
        if (value < 1e15 && value >= 0.0001) {
            int written = writeDoubleOptimized(value, buffer, pos);
            if (written > 0) {
                return pos + written;
            }
        }
        
        // Fall back to standard formatting
        return pos + writeDoubleFallback(value, buffer, pos);
    }
    
    /**
     * Write a float to the output cursor.
     * 
     * @param value the float value to write
     * @param output the output cursor
     */
    public static void writeFloat(float value, OutputCursor output) {
        writeDouble(value, output);
    }
    
    // Pre-computed byte arrays for special values
    private static final byte[] MIN_INT_BYTES = "-2147483648".getBytes();
    private static final byte[] MIN_LONG_BYTES = "-9223372036854775808".getBytes();
    private static final byte[] NAN_BYTES = "null".getBytes();  // JSON doesn't support NaN
    private static final byte[] POS_INF_BYTES = "null".getBytes();  // JSON doesn't support Infinity
    private static final byte[] NEG_INF_BYTES = "null".getBytes();  // JSON doesn't support -Infinity
    private static final byte[] ZERO_BYTES = "0.0".getBytes();
    private static final byte[] NEG_ZERO_BYTES = "-0.0".getBytes();
    
    /**
     * Write a positive int using the digit pairs table.
     * 
     * Note: This method allocates a temp array. For zero-allocation serialization,
     * use the buffer-based writeInt(value, buffer) method instead, which is what
     * JsonWriter uses internally.
     */
    private static void writePositiveInt(int value, OutputCursor output) {
        if (value < 10) {
            output.writeByte(DIGITS[value]);
            return;
        }
        if (value < 100) {
            output.writeByte(DIGIT_PAIRS[value * 2]);
            output.writeByte(DIGIT_PAIRS[value * 2 + 1]);
            return;
        }
        
        // Count digits
        int digits;
        if (value < 1000) digits = 3;
        else if (value < 10000) digits = 4;
        else if (value < 100000) digits = 5;
        else if (value < 1000000) digits = 6;
        else if (value < 10000000) digits = 7;
        else if (value < 100000000) digits = 8;
        else if (value < 1000000000) digits = 9;
        else digits = 10;
        
        // Write digits in reverse, then copy
        byte[] temp = new byte[digits];
        int pos = digits;
        
        while (value >= 100) {
            int q = value / 100;
            int r = value - q * 100;
            value = q;
            temp[--pos] = DIGIT_PAIRS[r * 2 + 1];
            temp[--pos] = DIGIT_PAIRS[r * 2];
        }
        
        if (value >= 10) {
            temp[--pos] = DIGIT_PAIRS[value * 2 + 1];
            temp[--pos] = DIGIT_PAIRS[value * 2];
        } else if (value > 0) {
            temp[--pos] = DIGITS[value];
        }
        
        output.writeBytes(temp, 0, digits);
    }
    
    /**
     * Write a positive int to a buffer starting at pos.
     * Returns the number of bytes written.
     */
    private static int writePositiveInt(int value, byte[] buffer, int pos) {
        if (value < 10) {
            buffer[pos] = DIGITS[value];
            return 1;
        }
        if (value < 100) {
            buffer[pos] = DIGIT_PAIRS[value * 2];
            buffer[pos + 1] = DIGIT_PAIRS[value * 2 + 1];
            return 2;
        }
        
        // Count digits
        int digits;
        if (value < 1000) digits = 3;
        else if (value < 10000) digits = 4;
        else if (value < 100000) digits = 5;
        else if (value < 1000000) digits = 6;
        else if (value < 10000000) digits = 7;
        else if (value < 100000000) digits = 8;
        else if (value < 1000000000) digits = 9;
        else digits = 10;
        
        int end = pos + digits;
        int p = end;
        
        while (value >= 100) {
            int q = value / 100;
            int r = value - q * 100;
            value = q;
            buffer[--p] = DIGIT_PAIRS[r * 2 + 1];
            buffer[--p] = DIGIT_PAIRS[r * 2];
        }
        
        if (value >= 10) {
            buffer[--p] = DIGIT_PAIRS[value * 2 + 1];
            buffer[--p] = DIGIT_PAIRS[value * 2];
        } else if (value > 0) {
            buffer[--p] = DIGITS[value];
        }
        
        return digits;
    }
    
    /**
     * Write a positive long using the digit pairs table.
     */
    private static void writePositiveLong(long value, OutputCursor output) {
        if (value < Integer.MAX_VALUE) {
            writePositiveInt((int) value, output);
            return;
        }
        
        // Count digits
        int digits = countDigits(value);
        
        // Write digits in reverse
        byte[] temp = new byte[digits];
        int pos = digits;
        
        while (value >= 100) {
            long q = value / 100;
            int r = (int) (value - q * 100);
            value = q;
            temp[--pos] = DIGIT_PAIRS[r * 2 + 1];
            temp[--pos] = DIGIT_PAIRS[r * 2];
        }
        
        if (value >= 10) {
            temp[--pos] = DIGIT_PAIRS[(int) value * 2 + 1];
            temp[--pos] = DIGIT_PAIRS[(int) value * 2];
        } else if (value > 0) {
            temp[--pos] = DIGITS[(int) value];
        }
        
        output.writeBytes(temp, 0, digits);
    }
    
    /**
     * Write a positive long to a buffer starting at pos.
     * Returns the number of bytes written.
     */
    private static int writePositiveLong(long value, byte[] buffer, int pos) {
        if (value < Integer.MAX_VALUE) {
            return writePositiveInt((int) value, buffer, pos);
        }
        
        int digits = countDigits(value);
        int end = pos + digits;
        int p = end;
        
        while (value >= 100) {
            long q = value / 100;
            int r = (int) (value - q * 100);
            value = q;
            buffer[--p] = DIGIT_PAIRS[r * 2 + 1];
            buffer[--p] = DIGIT_PAIRS[r * 2];
        }
        
        if (value >= 10) {
            buffer[--p] = DIGIT_PAIRS[(int) value * 2 + 1];
            buffer[--p] = DIGIT_PAIRS[(int) value * 2];
        } else if (value > 0) {
            buffer[--p] = DIGITS[(int) value];
        }
        
        return digits;
    }
    
    /**
     * Count the number of digits in a positive long.
     */
    private static int countDigits(long value) {
        if (value < 10L) return 1;
        if (value < 100L) return 2;
        if (value < 1000L) return 3;
        if (value < 10000L) return 4;
        if (value < 100000L) return 5;
        if (value < 1000000L) return 6;
        if (value < 10000000L) return 7;
        if (value < 100000000L) return 8;
        if (value < 1000000000L) return 9;
        if (value < 10000000000L) return 10;
        if (value < 100000000000L) return 11;
        if (value < 1000000000000L) return 12;
        if (value < 10000000000000L) return 13;
        if (value < 100000000000000L) return 14;
        if (value < 1000000000000000L) return 15;
        if (value < 10000000000000000L) return 16;
        if (value < 100000000000000000L) return 17;
        if (value < 1000000000000000000L) return 18;
        return 19;
    }
    
    /**
     * Optimized double formatting for common exchange price formats.
     * Returns true if successful, false if fallback is needed.
     */
    private static boolean writeDoubleOptimized(double value, OutputCursor output) {
        // Find the appropriate scale (number of decimal places)
        // For exchange prices, we typically want up to 8 decimal places
        int scale = 8;
        long multiplier = POWERS_OF_10[scale];
        
        // Scale the value to an integer
        long scaled = Math.round(value * multiplier);
        
        // Verify precision is maintained
        double reconstructed = scaled / (double) multiplier;
        if (Math.abs(reconstructed - value) > value * 1e-10 && Math.abs(reconstructed - value) > 1e-15) {
            return false;  // Precision loss, use fallback
        }
        
        // Remove trailing zeros from scale
        while (scale > 1 && scaled % 10 == 0) {
            scaled /= 10;
            scale--;
        }
        
        // Split into integer and fractional parts
        long divisor = POWERS_OF_10[scale];
        long intPart = scaled / divisor;
        long fracPart = scaled % divisor;
        
        // Write integer part
        if (intPart == 0) {
            output.writeByte((byte) '0');
        } else {
            writePositiveLong(intPart, output);
        }
        
        // Write decimal point and fractional part
        output.writeByte((byte) '.');
        
        // Pad with leading zeros if needed
        int fracDigits = countDigits(fracPart);
        while (fracDigits < scale) {
            output.writeByte((byte) '0');
            scale--;
        }
        
        if (fracPart > 0) {
            writePositiveLong(fracPart, output);
        }
        
        return true;
    }
    
    /**
     * Optimized double formatting to a buffer.
     * Returns bytes written, or 0 if fallback is needed.
     */
    private static int writeDoubleOptimized(double value, byte[] buffer, int pos) {
        int scale = 8;
        long multiplier = POWERS_OF_10[scale];
        long scaled = Math.round(value * multiplier);
        
        double reconstructed = scaled / (double) multiplier;
        if (Math.abs(reconstructed - value) > value * 1e-10 && Math.abs(reconstructed - value) > 1e-15) {
            return 0;
        }
        
        while (scale > 1 && scaled % 10 == 0) {
            scaled /= 10;
            scale--;
        }
        
        long divisor = POWERS_OF_10[scale];
        long intPart = scaled / divisor;
        long fracPart = scaled % divisor;
        
        int written = 0;
        
        if (intPart == 0) {
            buffer[pos + written++] = '0';
        } else {
            written += writePositiveLong(intPart, buffer, pos);
        }
        
        buffer[pos + written++] = '.';
        
        int fracDigits = countDigits(fracPart);
        while (fracDigits < scale) {
            buffer[pos + written++] = '0';
            scale--;
        }
        
        if (fracPart > 0) {
            written += writePositiveLong(fracPart, buffer, pos + written);
        }
        
        return written;
    }
    
    /**
     * Fallback double formatting using Double.toString().
     * This allocates a String but handles edge cases correctly.
     */
    private static void writeDoubleFallback(double value, OutputCursor output) {
        String str = Double.toString(value);
        int len = str.length();
        for (int i = 0; i < len; i++) {
            output.writeByte((byte) str.charAt(i));
        }
    }
    
    /**
     * Fallback double formatting to a buffer.
     */
    private static int writeDoubleFallback(double value, byte[] buffer, int pos) {
        String str = Double.toString(value);
        int len = str.length();
        for (int i = 0; i < len; i++) {
            buffer[pos + i] = (byte) str.charAt(i);
        }
        return len;
    }
    
    /**
     * Get the maximum number of bytes needed to serialize a long.
     * 
     * @return 20 (sign + 19 digits)
     */
    public static int maxLongBytes() {
        return MAX_LONG_DIGITS;
    }
    
    /**
     * Get the maximum number of bytes needed to serialize a double.
     * 
     * @return 32 bytes
     */
    public static int maxDoubleBytes() {
        return MAX_DOUBLE_DIGITS;
    }
}

