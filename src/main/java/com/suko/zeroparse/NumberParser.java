package com.suko.zeroparse;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Zero-allocation number parser that works directly on byte arrays.
 * 
 * <p>This parser eliminates the need to create intermediate String objects
 * when parsing JSON numbers, significantly reducing GC pressure in
 * high-throughput scenarios.</p>
 * 
 * <p>Supports parsing from UTF-8 encoded byte arrays for:</p>
 * <ul>
 *   <li>Integers (int, long, BigInteger)</li>
 *   <li>Floating point (float, double, BigDecimal)</li>
 *   <li>Scientific notation (1.23e-45)</li>
 * </ul>
 */
public final class NumberParser {
    
    private NumberParser() {
        // Utility class
    }
    
    /**
     * Parse a long from a byte array slice.
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed long value
     * @throws NumberFormatException if the number is invalid or out of range
     */
    public static long parseLong(byte[] bytes, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("Empty string");
        }
        
        boolean negative = false;
        int i = 0;
        long result = 0;
        
        // Check for negative sign
        if (bytes[offset] == '-') {
            if (length == 1) {
                throw new NumberFormatException("Just a minus sign");
            }
            negative = true;
            i = 1;
        }
        
        // Parse digits
        while (i < length) {
            byte b = bytes[offset + i];
            
            // Check if we have a decimal point or exponent (not an integer)
            if (b == '.' || b == 'e' || b == 'E') {
                // Fall back to parsing as double then converting
                double d = parseDouble(bytes, offset, length);
                if (d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
                    throw new NumberFormatException("Number out of long range");
                }
                return (long) d;
            }
            
            if (b < '0' || b > '9') {
                throw new NumberFormatException("Invalid digit: " + (char) b);
            }
            
            int digit = b - '0';
            
            // Check for overflow before multiplication
            if (result < Long.MIN_VALUE / 10) {
                throw new NumberFormatException("Number out of long range (overflow)");
            }
            
            result *= 10;
            
            // Check for overflow after addition
            if (negative) {
                if (result < Long.MIN_VALUE + digit) {
                    throw new NumberFormatException("Number out of long range (overflow)");
                }
                result -= digit;
            } else {
                if (result > Long.MAX_VALUE - digit) {
                    throw new NumberFormatException("Number out of long range (overflow)");
                }
                result += digit;
            }
            
            i++;
        }
        
        return negative ? result : result;
    }
    
    /**
     * Parse an int from a byte array slice.
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed int value
     * @throws NumberFormatException if the number is invalid or out of range
     */
    public static int parseInt(byte[] bytes, int offset, int length) {
        long value = parseLong(bytes, offset, length);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new NumberFormatException("Number out of int range");
        }
        return (int) value;
    }
    
    /**
     * Parse a double from a byte array slice.
     * 
     * <p>This implementation uses a custom parser for simple cases and falls back
     * to Java's Double.parseDouble() for complex scientific notation.</p>
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed double value
     * @throws NumberFormatException if the number is invalid
     */
    public static double parseDouble(byte[] bytes, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("Empty string");
        }
        
        // Quick path: check if we have scientific notation
        boolean hasExponent = false;
        for (int i = 0; i < length; i++) {
            byte b = bytes[offset + i];
            if (b == 'e' || b == 'E') {
                hasExponent = true;
                break;
            }
        }
        
        // For scientific notation, fall back to String parsing (rare case)
        if (hasExponent) {
            return Double.parseDouble(new String(bytes, offset, length));
        }
        
        // Manual parsing for common case: [-]digits[.digits]
        boolean negative = false;
        int i = 0;
        
        // Check for negative sign
        if (bytes[offset] == '-') {
            if (length == 1) {
                throw new NumberFormatException("Just a minus sign");
            }
            negative = true;
            i = 1;
        } else if (bytes[offset] == '+') {
            i = 1;
        }
        
        // Parse integer part
        long integerPart = 0;
        boolean hasIntegerPart = false;
        
        while (i < length) {
            byte b = bytes[offset + i];
            if (b >= '0' && b <= '9') {
                integerPart = integerPart * 10 + (b - '0');
                hasIntegerPart = true;
                i++;
            } else {
                break;
            }
        }
        
        // Parse fractional part
        double fractionalPart = 0.0;
        if (i < length && bytes[offset + i] == '.') {
            i++; // Skip decimal point
            
            double divisor = 10.0;
            while (i < length) {
                byte b = bytes[offset + i];
                if (b >= '0' && b <= '9') {
                    fractionalPart += (b - '0') / divisor;
                    divisor *= 10.0;
                    i++;
                } else {
                    break;
                }
            }
        }
        
        if (!hasIntegerPart && fractionalPart == 0.0) {
            throw new NumberFormatException("No digits found");
        }
        
        // Check that we consumed all bytes
        if (i != length) {
            throw new NumberFormatException("Unexpected character at position " + i);
        }
        
        double result = integerPart + fractionalPart;
        return negative ? -result : result;
    }
    
    /**
     * Parse a float from a byte array slice.
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed float value
     * @throws NumberFormatException if the number is invalid
     */
    public static float parseFloat(byte[] bytes, int offset, int length) {
        return (float) parseDouble(bytes, offset, length);
    }
    
    /**
     * Parse a BigDecimal from a byte array slice.
     * 
     * <p>Note: This method allocates a String as BigDecimal doesn't provide
     * a byte array constructor. Use only when precision is critical.</p>
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed BigDecimal value
     * @throws NumberFormatException if the number is invalid
     */
    public static BigDecimal parseBigDecimal(byte[] bytes, int offset, int length) {
        // BigDecimal requires String - but this is acceptable for high-precision use cases
        return new BigDecimal(new String(bytes, offset, length));
    }
    
    /**
     * Parse a BigInteger from a byte array slice.
     * 
     * <p>Note: This method allocates a String as BigInteger doesn't provide
     * a byte array constructor. Use only when precision is critical.</p>
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to parse
     * @return the parsed BigInteger value
     * @throws NumberFormatException if the number is invalid
     */
    public static BigInteger parseBigInteger(byte[] bytes, int offset, int length) {
        // BigInteger requires String - but this is acceptable for high-precision use cases
        return new BigInteger(new String(bytes, offset, length));
    }
    
    /**
     * Check if the number in the byte array is an integer (no decimal point or exponent).
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to check
     * @return true if the number is an integer
     */
    public static boolean isInteger(byte[] bytes, int offset, int length) {
        for (int i = 0; i < length; i++) {
            byte b = bytes[offset + i];
            if (b == '.' || b == 'e' || b == 'E') {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if the number in the byte array is negative.
     * 
     * @param bytes the byte array
     * @param offset the starting offset
     * @param length the number of bytes to check
     * @return true if the number is negative
     */
    public static boolean isNegative(byte[] bytes, int offset, int length) {
        return length > 0 && bytes[offset] == '-';
    }
}

