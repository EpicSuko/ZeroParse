package com.suko.zeroparse;

/**
 * Zero-allocation JSON string escaper.
 * 
 * <p>Uses pre-computed lookup tables for fast character escaping.
 * Writes escaped strings directly to output without intermediate allocations.</p>
 * 
 * <p>JSON requires escaping of:</p>
 * <ul>
 *   <li>Control characters (0x00-0x1F)</li>
 *   <li>Quotation mark (")</li>
 *   <li>Reverse solidus (\)</li>
 * </ul>
 */
public final class StringEscaper {
    
    // Escape lookup table: null means no escaping needed
    // For characters 0-127, we pre-compute the escape sequence
    private static final byte[][] ESCAPE_TABLE = new byte[128][];
    
    // Pre-computed hex digits for unicode escaping
    private static final byte[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    
    static {
        // Initialize escape sequences for control characters (0x00-0x1F)
        for (int i = 0; i < 32; i++) {
            ESCAPE_TABLE[i] = escapeControlChar(i);
        }
        
        // Common escape sequences
        ESCAPE_TABLE['"'] = new byte[] { '\\', '"' };
        ESCAPE_TABLE['\\'] = new byte[] { '\\', '\\' };
        ESCAPE_TABLE['\b'] = new byte[] { '\\', 'b' };
        ESCAPE_TABLE['\f'] = new byte[] { '\\', 'f' };
        ESCAPE_TABLE['\n'] = new byte[] { '\\', 'n' };
        ESCAPE_TABLE['\r'] = new byte[] { '\\', 'r' };
        ESCAPE_TABLE['\t'] = new byte[] { '\\', 't' };
    }
    
    private static byte[] escapeControlChar(int c) {
        // Already handled by short escapes?
        if (c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
            return null;  // Will be set in static block
        }
        // Use unicode escape format
        return new byte[] {
            '\\', 'u', '0', '0',
            HEX_DIGITS[(c >> 4) & 0xF],
            HEX_DIGITS[c & 0xF]
        };
    }
    
    private StringEscaper() {
        // Utility class
    }
    
    /**
     * Write a JSON-escaped string to the output (including quotes).
     * 
     * @param value the string to escape and write
     * @param output the output cursor
     */
    public static void writeString(String value, OutputCursor output) {
        output.writeByte((byte) '"');
        writeStringContents(value, output);
        output.writeByte((byte) '"');
    }
    
    /**
     * Write JSON-escaped string contents (without quotes).
     * 
     * @param value the string to escape and write
     * @param output the output cursor
     */
    public static void writeStringContents(String value, OutputCursor output) {
        int len = value.length();
        int start = 0;
        
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            
            byte[] escape = null;
            if (c < 128) {
                escape = ESCAPE_TABLE[c];
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                // Surrogate pairs - handled specially
                escape = null;  // UTF-8 encode normally
            }
            
            if (escape != null) {
                // Write any unescaped content before this character
                if (i > start) {
                    writeUtf8Segment(value, start, i, output);
                }
                // Write the escape sequence
                output.writeBytes(escape, 0, escape.length);
                start = i + 1;
            }
        }
        
        // Write remaining content
        if (start < len) {
            writeUtf8Segment(value, start, len, output);
        }
    }
    
    /**
     * Write a JSON-escaped ByteSlice to the output (including quotes).
     * Assumes the ByteSlice contains valid UTF-8.
     * 
     * @param slice the ByteSlice to escape and write
     * @param output the output cursor
     */
    public static void writeString(ByteSlice slice, OutputCursor output) {
        output.writeByte((byte) '"');
        writeStringContents(slice, output);
        output.writeByte((byte) '"');
    }
    
    /**
     * Write JSON-escaped ByteSlice contents (without quotes).
     * Assumes the ByteSlice contains valid UTF-8.
     * 
     * @param slice the ByteSlice to escape and write
     * @param output the output cursor
     */
    public static void writeStringContents(ByteSlice slice, OutputCursor output) {
        byte[] source = slice.getSource();
        int offset = slice.getOffset();
        int length = slice.getLength();
        int end = offset + length;
        int start = offset;
        
        for (int i = offset; i < end; i++) {
            byte b = source[i];
            
            // Only ASCII bytes need checking for escaping
            if (b >= 0) {
                byte[] escape = ESCAPE_TABLE[b];
                if (escape != null) {
                    // Write any content before this character
                    if (i > start) {
                        output.writeBytes(source, start, i - start);
                    }
                    // Write the escape sequence
                    output.writeBytes(escape, 0, escape.length);
                    start = i + 1;
                }
            }
            // Negative bytes are continuation bytes of multi-byte UTF-8 sequences
            // They don't need escaping
        }
        
        // Write remaining content
        if (start < end) {
            output.writeBytes(source, start, end - start);
        }
    }
    
    /**
     * Write a segment of a String as UTF-8 bytes.
     */
    private static void writeUtf8Segment(String value, int start, int end, OutputCursor output) {
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            
            if (c < 0x80) {
                // ASCII
                output.writeByte((byte) c);
            } else if (c < 0x800) {
                // 2-byte UTF-8
                output.writeByte((byte) (0xC0 | (c >> 6)));
                output.writeByte((byte) (0x80 | (c & 0x3F)));
            } else if (c >= 0xD800 && c <= 0xDBFF && i + 1 < end) {
                // High surrogate - look for low surrogate
                char low = value.charAt(i + 1);
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    // Valid surrogate pair - encode as 4-byte UTF-8
                    int codePoint = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
                    output.writeByte((byte) (0xF0 | (codePoint >> 18)));
                    output.writeByte((byte) (0x80 | ((codePoint >> 12) & 0x3F)));
                    output.writeByte((byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                    output.writeByte((byte) (0x80 | (codePoint & 0x3F)));
                    i++;  // Skip the low surrogate
                    continue;
                }
                // Unpaired high surrogate - encode as 3-byte UTF-8 (replacement char)
                output.writeByte((byte) 0xEF);
                output.writeByte((byte) 0xBF);
                output.writeByte((byte) 0xBD);
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                // Unpaired low surrogate - encode as replacement char
                output.writeByte((byte) 0xEF);
                output.writeByte((byte) 0xBF);
                output.writeByte((byte) 0xBD);
            } else {
                // 3-byte UTF-8
                output.writeByte((byte) (0xE0 | (c >> 12)));
                output.writeByte((byte) (0x80 | ((c >> 6) & 0x3F)));
                output.writeByte((byte) (0x80 | (c & 0x3F)));
            }
        }
    }
    
    /**
     * Check if a string needs escaping.
     * Useful for optimization when you know the string is safe.
     * 
     * @param value the string to check
     * @return true if escaping is required
     */
    public static boolean needsEscaping(String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 128 && ESCAPE_TABLE[c] != null) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if a ByteSlice needs escaping.
     * 
     * @param slice the ByteSlice to check
     * @return true if escaping is required
     */
    public static boolean needsEscaping(ByteSlice slice) {
        byte[] source = slice.getSource();
        int offset = slice.getOffset();
        int length = slice.getLength();
        int end = offset + length;
        
        for (int i = offset; i < end; i++) {
            byte b = source[i];
            if (b >= 0 && ESCAPE_TABLE[b] != null) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Calculate the escaped length of a string (excluding quotes).
     * Useful for pre-sizing buffers.
     * 
     * @param value the string to measure
     * @return the number of bytes needed for the escaped content
     */
    public static int escapedLength(String value) {
        int len = value.length();
        int escaped = 0;
        
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                byte[] esc = ESCAPE_TABLE[c];
                if (esc != null) {
                    escaped += esc.length;
                } else {
                    escaped++;
                }
            } else if (c < 0x800) {
                escaped += 2;
            } else if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len) {
                char low = value.charAt(i + 1);
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    escaped += 4;
                    i++;
                } else {
                    escaped += 3;  // Replacement char
                }
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                escaped += 3;  // Replacement char
            } else {
                escaped += 3;
            }
        }
        
        return escaped;
    }
}

