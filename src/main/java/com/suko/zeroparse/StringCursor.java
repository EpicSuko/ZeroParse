package com.suko.zeroparse;

import java.nio.charset.StandardCharsets;

/**
 * InputCursor implementation for String input.
 * 
 * <p>This implementation reads UTF-16 characters from a String and provides
 * UTF-8 slice operations by converting the string to UTF-8 bytes on demand.</p>
 */
public final class StringCursor implements InputCursor {
    
    private final String string;
    private final byte[] utf8Bytes;
    
    /**
     * Create a new StringCursor.
     * 
     * @param string the String to read from
     */
    public StringCursor(String string) {
        if (string == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        this.string = string;
        this.utf8Bytes = string.getBytes(StandardCharsets.UTF_8);
    }
    
    @Override
    public int length() {
        return string.length();
    }
    
    @Override
    public byte byteAt(int index) {
        if (index < 0 || index >= string.length()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + string.length());
        }
        // Convert character to byte (lossy for non-ASCII)
        char c = string.charAt(index);
        return (byte) (c & 0xFF);
    }
    
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= string.length()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + string.length());
        }
        return string.charAt(index);
    }
    
    @Override
    public Utf8Slice slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > string.length()) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + length + ", stringLength=" + string.length());
        }
        
        // For String input, we need to convert the substring to UTF-8 bytes
        String substring = string.substring(start, start + length);
        byte[] bytes = substring.getBytes(StandardCharsets.UTF_8);
        return new Utf8Slice(bytes, 0, bytes.length);
    }
    
    /**
     * Get the underlying String.
     * 
     * @return the String
     */
    public String getString() {
        return string;
    }
    
    /**
     * Get the UTF-8 bytes of the entire string.
     * 
     * @return the UTF-8 bytes
     */
    public byte[] getUtf8Bytes() {
        return utf8Bytes;
    }
}
