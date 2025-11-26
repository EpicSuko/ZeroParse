package com.suko.zeroparse;

import java.nio.charset.StandardCharsets;

/**
 * InputCursor implementation for String input.
 * 
 * <p>This implementation reads UTF-16 characters from a String and provides
 * UTF-8 slice operations by converting the string to UTF-8 bytes on demand.</p>
 */
public final class StringCursor implements InputCursor {
    
    private String string;
    private byte[] utf8Bytes;
    // Optional parse context for pooled slice creation
    private JsonParseContext context;
    
    /**
     * Create a new StringCursor.
     * 
     * @param string the String to read from
     */
    public StringCursor(String string) {
        reset(string);
    }

    /**
     * Default constructor for pooling.
     */
    public StringCursor() {
        this.string = "";
        this.utf8Bytes = new byte[0];
    }

    public void reset(String string) {
        if (string == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        this.string = string;
        this.utf8Bytes = string.getBytes(StandardCharsets.UTF_8);
    }
    
    @Override
    public int length() {
        return utf8Bytes.length;
    }
    
    @Override
    public byte byteAt(int index) {
        if (index < 0 || index >= utf8Bytes.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + utf8Bytes.length);
        }
        return utf8Bytes[index];
    }
    
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= utf8Bytes.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + utf8Bytes.length);
        }
        return (char) (utf8Bytes[index] & 0xFF);
    }
    
    @Override
    public ByteSlice slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > string.length()) {
            throw new IndexOutOfBoundsException("Invalid slice: start=" + start + ", length=" + length + ", stringLength=" + string.length());
        }
        
        // Use pooled slice when a context is attached, otherwise allocate
        JsonParseContext ctx = this.context;
        if (ctx != null) {
            return ctx.borrowSlice(utf8Bytes, start, length);
        }
        
        return new ByteSlice(utf8Bytes, start, length);
    }
    
    @Override
    public byte[] getUnderlyingBytes() {
        return utf8Bytes;
    }
    
    @Override
    public int getUnderlyingOffset(int position) {
        // For StringCursor, we need to calculate the UTF-8 byte offset
        // This is expensive, so it's best to avoid for StringCursor
        // Return position as-is (works for ASCII, not for multi-byte UTF-8)
        return position;
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

    void setContext(JsonParseContext context) {
        this.context = context;
    }
}
