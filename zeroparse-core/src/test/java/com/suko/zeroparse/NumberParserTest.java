package com.suko.zeroparse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Comprehensive tests for zero-allocation NumberParser.
 */
public class NumberParserTest {
    
    // Helper to convert string to byte array
    private byte[] bytes(String s) {
        return s.getBytes();
    }
    
    @Test
    public void testParseInteger() {
        assertEquals(0, NumberParser.parseInt(bytes("0"), 0, 1));
        assertEquals(123, NumberParser.parseInt(bytes("123"), 0, 3));
        assertEquals(-456, NumberParser.parseInt(bytes("-456"), 0, 4));
        assertEquals(2147483647, NumberParser.parseInt(bytes("2147483647"), 0, 10));
        assertEquals(-2147483648, NumberParser.parseInt(bytes("-2147483648"), 0, 11));
    }
    
    @Test
    public void testParseLong() {
        assertEquals(0L, NumberParser.parseLong(bytes("0"), 0, 1));
        assertEquals(123L, NumberParser.parseLong(bytes("123"), 0, 3));
        assertEquals(-456L, NumberParser.parseLong(bytes("-456"), 0, 4));
        assertEquals(9223372036854775807L, NumberParser.parseLong(bytes("9223372036854775807"), 0, 19));
        assertEquals(-9223372036854775808L, NumberParser.parseLong(bytes("-9223372036854775808"), 0, 20));
    }
    
    @Test
    public void testParseDouble() {
        assertEquals(0.0, NumberParser.parseDouble(bytes("0"), 0, 1), 0.0);
        assertEquals(0.0, NumberParser.parseDouble(bytes("0.0"), 0, 3), 0.0);
        assertEquals(123.456, NumberParser.parseDouble(bytes("123.456"), 0, 7), 0.000001);
        assertEquals(-789.123, NumberParser.parseDouble(bytes("-789.123"), 0, 8), 0.000001);
        assertEquals(0.5, NumberParser.parseDouble(bytes("0.5"), 0, 3), 0.000001);
        assertEquals(-0.5, NumberParser.parseDouble(bytes("-0.5"), 0, 4), 0.000001);
    }
    
    @Test
    public void testParseFloat() {
        assertEquals(0.0f, NumberParser.parseFloat(bytes("0"), 0, 1), 0.0f);
        assertEquals(123.456f, NumberParser.parseFloat(bytes("123.456"), 0, 7), 0.001f);
        assertEquals(-789.123f, NumberParser.parseFloat(bytes("-789.123"), 0, 8), 0.001f);
    }
    
    @Test
    public void testParseTradingPrices() {
        // Real-world crypto trading examples
        assertEquals(27000.5, NumberParser.parseDouble(bytes("27000.5"), 0, 7), 0.000001);
        assertEquals(8.760, NumberParser.parseDouble(bytes("8.760"), 0, 5), 0.000001);
        assertEquals(0.00012345, NumberParser.parseDouble(bytes("0.00012345"), 0, 10), 0.0000000001);
    }
    
    @Test
    public void testParseScientificNotation() {
        // Should fall back to String parsing for scientific notation
        assertEquals(1.23e10, NumberParser.parseDouble(bytes("1.23e10"), 0, 7), 0.01);
        assertEquals(1.23e-10, NumberParser.parseDouble(bytes("1.23e-10"), 0, 8), 1e-15);
        assertEquals(-5.6E+20, NumberParser.parseDouble(bytes("-5.6E+20"), 0, 8), 1e15);
    }
    
    @Test
    public void testParseDoubleToLong() {
        // Should handle converting float to long
        assertEquals(123L, NumberParser.parseLong(bytes("123.0"), 0, 5));
        assertEquals(-456L, NumberParser.parseLong(bytes("-456.00"), 0, 7));
    }
    
    @Test
    public void testIsInteger() {
        assertTrue(NumberParser.isInteger(bytes("123"), 0, 3));
        assertTrue(NumberParser.isInteger(bytes("-456"), 0, 4));
        assertFalse(NumberParser.isInteger(bytes("123.456"), 0, 7));
        assertFalse(NumberParser.isInteger(bytes("1.23e10"), 0, 7));
    }
    
    @Test
    public void testIsNegative() {
        assertFalse(NumberParser.isNegative(bytes("123"), 0, 3));
        assertTrue(NumberParser.isNegative(bytes("-456"), 0, 4));
        assertFalse(NumberParser.isNegative(bytes("0"), 0, 1));
    }
    
    @Test
    public void testParseBigDecimal() {
        assertEquals(new BigDecimal("123.456789012345678901234567890"), 
                     NumberParser.parseBigDecimal(bytes("123.456789012345678901234567890"), 0, 31));
    }
    
    @Test
    public void testParseBigInteger() {
        assertEquals(new BigInteger("12345678901234567890"), 
                     NumberParser.parseBigInteger(bytes("12345678901234567890"), 0, 20));
    }
    
    @Test
    public void testInvalidNumbers() {
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseInt(bytes("abc"), 0, 3));
        
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseInt(bytes(""), 0, 0));
        
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseInt(bytes("-"), 0, 1));
        
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseDouble(bytes("12.34.56"), 0, 8));
    }
    
    @Test
    public void testOverflow() {
        // Integer overflow
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseInt(bytes("2147483648"), 0, 10)); // MAX_INT + 1
        
        assertThrows(NumberFormatException.class, () -> 
            NumberParser.parseInt(bytes("-2147483649"), 0, 11)); // MIN_INT - 1
    }
    
    @Test
    public void testEdgeCases() {
        // Leading zeros are handled by JSON spec
        assertEquals(0, NumberParser.parseInt(bytes("0"), 0, 1));
        
        // Positive sign
        assertEquals(123.0, NumberParser.parseDouble(bytes("+123"), 0, 4), 0.001);
        
        // Multiple decimal places
        assertEquals(0.123456789, NumberParser.parseDouble(bytes("0.123456789"), 0, 11), 0.000000001);
    }
    
    @Test
    public void testWithOffset() {
        // Test parsing with non-zero offset
        byte[] data = bytes("xxx123yyy");
        assertEquals(123, NumberParser.parseInt(data, 3, 3));
        
        byte[] data2 = bytes("xxx456.789yyy");
        assertEquals(456.789, NumberParser.parseDouble(data2, 3, 7), 0.001);
    }
}

