package com.suko.zeroparse;

/**
 * Abstract interface for reading JSON input from various sources.
 * 
 * <p>This interface provides a unified way to access JSON data from different
 * input types (Buffer, byte[], String) while maintaining zero-copy semantics
 * where possible.</p>
 */
public interface InputCursor {
    
    /**
     * Get the total length of the input.
     * 
     * @return the length in bytes or characters
     */
    int length();
    
    /**
     * Get a byte at the specified position.
     * 
     * @param index the position (0-based)
     * @return the byte at that position
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    byte byteAt(int index);
    
    /**
     * Get a character at the specified position.
     * 
     * @param index the position (0-based)
     * @return the character at that position
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    char charAt(int index);
    
    /**
     * Create a UTF-8 slice of the input.
     * 
     * @param start the starting position
     * @param length the length of the slice
     * @return a Utf8Slice representing the specified range
     * @throws IndexOutOfBoundsException if start or length is invalid
     */
    Utf8Slice slice(int start, int length);
    
    /**
     * Check if the input is empty.
     * 
     * @return true if the length is 0
     */
    default boolean isEmpty() {
        return length() == 0;
    }
    
    /**
     * Get a substring of the input.
     * 
     * @param start the starting position
     * @param end the ending position (exclusive)
     * @return the substring
     * @throws IndexOutOfBoundsException if start or end is invalid
     */
    default String substring(int start, int end) {
        if (start < 0 || end < start || end > length()) {
            throw new IndexOutOfBoundsException("Invalid substring range: " + start + " to " + end);
        }
        return slice(start, end - start).toString();
    }
}
