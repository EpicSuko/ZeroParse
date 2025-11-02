package com.suko.zeroparse.stack;

import com.suko.zeroparse.BufferCursor;
import com.suko.zeroparse.ByteArrayCursor;
import com.suko.zeroparse.InputCursor;
import com.suko.zeroparse.JsonParseException;

/**
 * Single-pass, stack-based JSON tokenizer that builds an AST.
 * 
 * <p>This tokenizer processes JSON input in a single pass, building a tree
 * structure using a manual stack. It defers all value materialization until
 * access, storing only position and type information.</p>
 */
public final class StackTokenizer {
    
    // Character constants
    private static final byte CH_SPACE = ' ';
    private static final byte CH_TAB = '\t';
    private static final byte CH_LINEFEED = '\n';
    private static final byte CH_CARRIAGE_RETURN = '\r';
    private static final byte CH_QUOTE = '"';
    private static final byte CH_BACKSLASH = '\\';
    private static final byte CH_COLON = ':';
    private static final byte CH_COMMA = ',';
    private static final byte CH_BEGIN_CURLY = '{';
    private static final byte CH_END_CURLY = '}';
    private static final byte CH_BEGIN_BRACKET = '[';
    private static final byte CH_END_BRACKET = ']';
    private static final byte CH_DASH = '-';
    private static final byte CH_PLUS = '+';
    private static final byte CH_DOT = '.';
    private static final byte CH_E = 'e';
    private static final byte CH_E_UPPER = 'E';
    private static final byte CH_0 = '0';
    private static final byte CH_1 = '1';
    private static final byte CH_2 = '2';
    private static final byte CH_3 = '3';
    private static final byte CH_4 = '4';
    private static final byte CH_5 = '5';
    private static final byte CH_6 = '6';
    private static final byte CH_7 = '7';
    private static final byte CH_8 = '8';
    private static final byte CH_9 = '9';
    
    // Literal constants
    private static final byte[] TRUE_BYTES = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE_BYTES = {'f', 'a', 'l', 's', 'e'};
    private static final byte[] NULL_BYTES = {'n', 'u', 'l', 'l'};
    
    // Stack management
    private final int[] stack;
    private int stackPointer;
    private final AstStore astStore;
    
    // Input tracking
    private InputCursor cursor;
    private int currentOffset;
    private int inputLength; // Cache length to avoid method calls
    
    // Fast path: direct byte array access (avoids virtual method calls)
    private byte[] fastBytes;
    private int fastBytesOffset;
    
    /**
     * Create a new StackTokenizer.
     */
    public StackTokenizer() {
        this.astStore = new AstStore();
        this.stack = new int[64]; // Initial stack size
        this.stackPointer = 0;
    }
    
    /**
     * Tokenize JSON input and build AST.
     * 
     * @param cursor the input cursor
     * @return the AST store with parsed nodes
     * @throws JsonParseException if parsing fails
     */
    public AstStore tokenize(InputCursor cursor) throws JsonParseException {
        this.cursor = cursor;
        this.currentOffset = 0;
        this.inputLength = cursor.length(); // Cache length
        this.stackPointer = 0;
        this.astStore.reset();
        
        // Fast path optimization: get direct byte array access if available
        if (cursor instanceof BufferCursor) {
            this.fastBytes = ((BufferCursor) cursor).getCachedBytes();
            this.fastBytesOffset = 0;
        } else if (cursor instanceof ByteArrayCursor) {
            this.fastBytes = ((ByteArrayCursor) cursor).getData();
            this.fastBytesOffset = ((ByteArrayCursor) cursor).getOffset();
        } else {
            this.fastBytes = null;
            this.fastBytesOffset = 0;
        }
        
        // Skip initial whitespace
        skipWhitespace();
        
        if (currentOffset >= inputLength) {
            throw new JsonParseException("Empty JSON input", currentOffset);
        }
        
        // Parse root value - inline fast path
        byte firstByte = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
        int rootIndex;
        
        if (firstByte == CH_BEGIN_CURLY) {
            rootIndex = parseObject();
        } else if (firstByte == CH_BEGIN_BRACKET) {
            rootIndex = parseArray();
        } else {
            // Parse primitive value
            rootIndex = parseValue();
        }
        
        astStore.setRoot(rootIndex);
        
        // Ensure we consumed all input
        skipWhitespace();
        if (currentOffset < inputLength) {
            throw new JsonParseException("Unexpected content after JSON", currentOffset);
        }
        
        return astStore;
    }
    
    private int parseObject() throws JsonParseException {
        int start = currentOffset;
        currentOffset++; // Skip opening brace
        
        int objectIndex = astStore.addNode(AstStore.TYPE_OBJECT, start, -1);
        pushStack(objectIndex);
        
        skipWhitespace();
        
        // Check for empty object - inline fast path
        if (currentOffset < inputLength) {
            byte b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
            if (b == CH_END_CURLY) {
                currentOffset++; // Skip closing brace
                astStore.setEnd(objectIndex, currentOffset);
                popStack();
                return objectIndex;
            }
        }
        
        boolean expectValue = false;
        boolean firstField = true;
        
        while (currentOffset < inputLength) {
            skipWhitespace();
            
            if (currentOffset >= inputLength) {
                throw new JsonParseException("Unexpected end of input in object", currentOffset);
            }
            
            byte b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
            
            if (b == CH_END_CURLY) {
                currentOffset++; // Skip closing brace
                astStore.setEnd(objectIndex, currentOffset);
                popStack();
                
                if (expectValue) {
                    throw new JsonParseException("Unexpected comma without value", currentOffset - 1);
                }
                break;
            } else if (b == CH_COMMA) {
                if (expectValue) {
                    throw new JsonParseException("Unexpected comma", currentOffset);
                }
                if (firstField) {
                    throw new JsonParseException("Expected field name before comma", currentOffset);
                }
                currentOffset++; // Skip comma
                expectValue = true;
            } else if (b == CH_QUOTE) {
                // Parse field name WITH HASH COMPUTATION (for fast lookups)
                int fieldNameIndex = parseString(true);
                
                // Expect colon
                skipWhitespace();
                if (currentOffset >= inputLength) {
                    throw new JsonParseException("Expected ':' after field name", currentOffset);
                }
                b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
                if (b != CH_COLON) {
                    throw new JsonParseException("Expected ':' after field name", currentOffset);
                }
                currentOffset++; // Skip colon
                
                // Parse field value
                skipWhitespace();
                int fieldValueIndex = parseValue();
                
                // Create a field node that contains name and value
                int fieldNodeIndex = astStore.addNode(AstStore.TYPE_FIELD, fieldNameIndex, fieldValueIndex);
                astStore.setFirstChild(fieldNodeIndex, fieldNameIndex);
                astStore.setNextSibling(fieldNameIndex, fieldValueIndex);
                astStore.addChild(objectIndex, fieldNodeIndex);
                
                expectValue = false;
                firstField = false;
            } else {
                throw new JsonParseException("Expected field name or '}'", currentOffset);
            }
        }
        
        return objectIndex;
    }
    
    private int parseArray() throws JsonParseException {
        int start = currentOffset;
        currentOffset++; // Skip opening bracket
        
        int arrayIndex = astStore.addNode(AstStore.TYPE_ARRAY, start, -1);
        pushStack(arrayIndex);
        
        skipWhitespace();
        
        // Check for empty array - inline fast path
        if (currentOffset < inputLength) {
            byte b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
            if (b == CH_END_BRACKET) {
                currentOffset++; // Skip closing bracket
                astStore.setEnd(arrayIndex, currentOffset);
                popStack();
                return arrayIndex;
            }
        }
        
        boolean expectValue = false;
        boolean firstElement = true;
        
        while (currentOffset < inputLength) {
            skipWhitespace();
            
            if (currentOffset >= inputLength) {
                throw new JsonParseException("Unexpected end of input in array", currentOffset);
            }
            
            byte b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
            
            if (b == CH_END_BRACKET) {
                currentOffset++; // Skip closing bracket
                astStore.setEnd(arrayIndex, currentOffset);
                popStack();
                
                if (expectValue) {
                    throw new JsonParseException("Unexpected comma without value", currentOffset - 1);
                }
                break;
            } else if (b == CH_COMMA) {
                if (expectValue) {
                    throw new JsonParseException("Unexpected comma", currentOffset);
                }
                if (firstElement) {
                    throw new JsonParseException("Expected value before comma", currentOffset);
                }
                currentOffset++; // Skip comma
                expectValue = true;
            } else {
                // Parse array element
                int elementIndex = parseValue();
                astStore.addChild(arrayIndex, elementIndex);
                
                expectValue = false;
                firstElement = false;
            }
        }
        
        return arrayIndex;
    }
    
    private int parseValue() throws JsonParseException {
        skipWhitespace();
        
        if (currentOffset >= inputLength) {
            throw new JsonParseException("Unexpected end of input", currentOffset);
        }
        
        byte b = (fastBytes != null) ? fastBytes[fastBytesOffset + currentOffset] : cursor.byteAt(currentOffset);
        
        switch (b) {
            case CH_QUOTE:
                return parseString();
            case CH_BEGIN_CURLY:
                return parseObject();
            case CH_BEGIN_BRACKET:
                return parseArray();
            case 't':
                return parseTrue();
            case 'f':
                return parseFalse();
            case 'n':
                return parseNull();
            case CH_DASH:
            case CH_0:
            case CH_1:
            case CH_2:
            case CH_3:
            case CH_4:
            case CH_5:
            case CH_6:
            case CH_7:
            case CH_8:
            case CH_9:
                return parseNumber();
            default:
                throw new JsonParseException("Unexpected character: " + (char) b, currentOffset);
        }
    }
    
    private int parseString() throws JsonParseException {
        return parseString(false);
    }
    
    private int parseString(boolean computeHash) throws JsonParseException {
        int start = currentOffset;
        int offset = currentOffset + 1; // Skip opening quote
        final int length = inputLength;
        boolean escaped = false;
        int hashCode = 0;
        
        // Find the end of the string, optionally computing hashcode
        // Optimized tight loop with direct byte access for maximum performance
        if (fastBytes != null) {
            // Fast path: direct array access
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset;
            
            if (computeHash) {
                // Hash computation path (for field names only)
                while (offset < length) {
                    byte b = bytes[baseOffset + offset];
                    if (b == CH_QUOTE) {
                        currentOffset = offset + 1;
                        int flags = escaped ? AstStore.FLAG_STRING_ESCAPED : 0;
                        return astStore.addNode(AstStore.TYPE_STRING, start + 1, offset, flags, hashCode);
                    } else if (b == CH_BACKSLASH) {
                        offset++;
                        if (offset >= length) {
                            throw new JsonParseException("Incomplete escape sequence", offset);
                        }
                        // For escaped strings, compute hash of the escape sequence bytes
                        hashCode = 31 * hashCode + bytes[baseOffset + offset];
                        escaped = true;
                        offset++;
                    } else {
                        // Decode UTF-8 and hash the character value (matches String.hashCode())
                        if ((b & 0x80) == 0) {
                            // ASCII (1 byte) - fast path
                            hashCode = 31 * hashCode + b;
                            offset++;
                        } else if ((b & 0xE0) == 0xC0) {
                            // 2-byte sequence
                            if (offset + 1 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x1F) << 6) | (bytes[baseOffset + offset + 1] & 0x3F);
                            hashCode = 31 * hashCode + codePoint;
                            offset += 2;
                        } else if ((b & 0xF0) == 0xE0) {
                            // 3-byte sequence
                            if (offset + 2 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x0F) << 12) 
                                          | ((bytes[baseOffset + offset + 1] & 0x3F) << 6)
                                          | (bytes[baseOffset + offset + 2] & 0x3F);
                            hashCode = 31 * hashCode + codePoint;
                            offset += 3;
                        } else {
                            // 4-byte sequence (rare, produces surrogate pair)
                            if (offset + 3 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x07) << 18)
                                          | ((bytes[baseOffset + offset + 1] & 0x3F) << 12)
                                          | ((bytes[baseOffset + offset + 2] & 0x3F) << 6)
                                          | (bytes[baseOffset + offset + 3] & 0x3F);
                            // Hash as surrogate pair (matches String.hashCode())
                            int high = ((codePoint >> 10) + 0xD7C0);
                            int low = ((codePoint & 0x3FF) + 0xDC00);
                            hashCode = 31 * hashCode + high;
                            hashCode = 31 * hashCode + low;
                            offset += 4;
                        }
                    }
                }
            } else {
                // Fast path without hash computation (for string values)
                while (offset < length) {
                    byte b = bytes[baseOffset + offset];
                    if (b == CH_QUOTE) {
                        currentOffset = offset + 1;
                        int flags = escaped ? AstStore.FLAG_STRING_ESCAPED : 0;
                        return astStore.addNode(AstStore.TYPE_STRING, start + 1, offset, flags);
                    } else if (b == CH_BACKSLASH) {
                        offset++;
                        if (offset >= length) {
                            throw new JsonParseException("Incomplete escape sequence", offset);
                        }
                        escaped = true;
                        offset++;
                    } else {
                        offset++;
                    }
                }
            }
        } else {
            // Slow path: cursor access
            if (computeHash) {
                // Hash computation path
                while (offset < length) {
                    byte b = cursor.byteAt(offset);
                    if (b == CH_QUOTE) {
                        currentOffset = offset + 1;
                        int flags = escaped ? AstStore.FLAG_STRING_ESCAPED : 0;
                        return astStore.addNode(AstStore.TYPE_STRING, start + 1, offset, flags, hashCode);
                    } else if (b == CH_BACKSLASH) {
                        offset++;
                        if (offset >= length) {
                            throw new JsonParseException("Incomplete escape sequence", offset);
                        }
                        hashCode = 31 * hashCode + cursor.byteAt(offset);
                        escaped = true;
                        offset++;
                    } else {
                        // Decode UTF-8 and hash the character value
                        if ((b & 0x80) == 0) {
                            // ASCII (1 byte)
                            hashCode = 31 * hashCode + b;
                            offset++;
                        } else if ((b & 0xE0) == 0xC0) {
                            // 2-byte sequence
                            if (offset + 1 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x1F) << 6) | (cursor.byteAt(offset + 1) & 0x3F);
                            hashCode = 31 * hashCode + codePoint;
                            offset += 2;
                        } else if ((b & 0xF0) == 0xE0) {
                            // 3-byte sequence
                            if (offset + 2 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x0F) << 12) 
                                          | ((cursor.byteAt(offset + 1) & 0x3F) << 6)
                                          | (cursor.byteAt(offset + 2) & 0x3F);
                            hashCode = 31 * hashCode + codePoint;
                            offset += 3;
                        } else {
                            // 4-byte sequence (rare)
                            if (offset + 3 >= length) {
                                throw new JsonParseException("Invalid UTF-8 sequence", offset);
                            }
                            int codePoint = ((b & 0x07) << 18)
                                          | ((cursor.byteAt(offset + 1) & 0x3F) << 12)
                                          | ((cursor.byteAt(offset + 2) & 0x3F) << 6)
                                          | (cursor.byteAt(offset + 3) & 0x3F);
                            int high = ((codePoint >> 10) + 0xD7C0);
                            int low = ((codePoint & 0x3FF) + 0xDC00);
                            hashCode = 31 * hashCode + high;
                            hashCode = 31 * hashCode + low;
                            offset += 4;
                        }
                    }
                }
            } else {
                // Fast path without hash computation
                while (offset < length) {
                    byte b = cursor.byteAt(offset);
                    if (b == CH_QUOTE) {
                        currentOffset = offset + 1;
                        int flags = escaped ? AstStore.FLAG_STRING_ESCAPED : 0;
                        return astStore.addNode(AstStore.TYPE_STRING, start + 1, offset, flags);
                    } else if (b == CH_BACKSLASH) {
                        offset++;
                        if (offset >= length) {
                            throw new JsonParseException("Incomplete escape sequence", offset);
                        }
                        escaped = true;
                        offset++;
                    } else {
                        offset++;
                    }
                }
            }
        }
        
        throw new JsonParseException("Unterminated string", start);
    }
    
    private int parseNumber() throws JsonParseException {
        int start = currentOffset;
        int offset = currentOffset;
        final int length = inputLength;
        boolean isFloat = false;
        byte b;
        
        // Inline fast path for maximum performance in number parsing
        if (fastBytes != null) {
            // Fast path: direct array access
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset;
            
            // Handle negative sign
            if (offset < length && bytes[baseOffset + offset] == CH_DASH) {
                offset++;
                if (offset >= length || !isDigit(bytes[baseOffset + offset])) {
                    throw new JsonParseException("Digit expected after minus sign", offset);
                }
            }
            
            // Parse integer part
            b = bytes[baseOffset + offset];
            if (b == CH_0) {
                offset++;
            } else {
                if (!isDigit(b)) {
                    throw new JsonParseException("Digit expected", offset);
                }
                do {
                    offset++;
                } while (offset < length && isDigit(bytes[baseOffset + offset]));
            }
            
            // Parse fractional part
            if (offset < length && bytes[baseOffset + offset] == CH_DOT) {
                isFloat = true;
                offset++;
                if (offset >= length || !isDigit(bytes[baseOffset + offset])) {
                    throw new JsonParseException("Digit expected after decimal point", offset);
                }
                do {
                    offset++;
                } while (offset < length && isDigit(bytes[baseOffset + offset]));
            }
            
            // Parse exponent
            if (offset < length) {
                b = bytes[baseOffset + offset];
                if (b == CH_E || b == CH_E_UPPER) {
                    isFloat = true;
                    offset++;
                    if (offset < length) {
                        b = bytes[baseOffset + offset];
                        if (b == CH_DASH || b == CH_PLUS) {
                            offset++;
                        }
                    }
                    if (offset >= length || !isDigit(bytes[baseOffset + offset])) {
                        throw new JsonParseException("Digit expected in exponent", offset);
                    }
                    do {
                        offset++;
                    } while (offset < length && isDigit(bytes[baseOffset + offset]));
                }
            }
        } else {
            // Slow path: cursor access
            if (offset < length && cursor.byteAt(offset) == CH_DASH) {
                offset++;
                if (offset >= length || !isDigit(cursor.byteAt(offset))) {
                    throw new JsonParseException("Digit expected after minus sign", offset);
                }
            }
            
            b = cursor.byteAt(offset);
            if (b == CH_0) {
                offset++;
            } else {
                if (!isDigit(b)) {
                    throw new JsonParseException("Digit expected", offset);
                }
                do {
                    offset++;
                } while (offset < length && isDigit(cursor.byteAt(offset)));
            }
            
            if (offset < length && cursor.byteAt(offset) == CH_DOT) {
                isFloat = true;
                offset++;
                if (offset >= length || !isDigit(cursor.byteAt(offset))) {
                    throw new JsonParseException("Digit expected after decimal point", offset);
                }
                do {
                    offset++;
                } while (offset < length && isDigit(cursor.byteAt(offset)));
            }
            
            if (offset < length) {
                b = cursor.byteAt(offset);
                if (b == CH_E || b == CH_E_UPPER) {
                    isFloat = true;
                    offset++;
                    if (offset < length) {
                        b = cursor.byteAt(offset);
                        if (b == CH_DASH || b == CH_PLUS) {
                            offset++;
                        }
                    }
                    if (offset >= length || !isDigit(cursor.byteAt(offset))) {
                        throw new JsonParseException("Digit expected in exponent", offset);
                    }
                    do {
                        offset++;
                    } while (offset < length && isDigit(cursor.byteAt(offset)));
                }
            }
        }
        
        currentOffset = offset;
        int flags = isFloat ? AstStore.FLAG_NUMBER_FLOAT : 0;
        return astStore.addNode(AstStore.TYPE_NUMBER, start, currentOffset, flags);
    }
    
    private int parseTrue() throws JsonParseException {
        int start = currentOffset;
        if (currentOffset + 4 > inputLength) {
            throw new JsonParseException("Expected 'true'", currentOffset);
        }
        
        if (fastBytes != null) {
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset + currentOffset;
            for (int i = 0; i < 4; i++) {
                if (bytes[baseOffset + i] != TRUE_BYTES[i]) {
                    throw new JsonParseException("Expected 'true'", currentOffset);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                if (cursor.byteAt(currentOffset + i) != TRUE_BYTES[i]) {
                    throw new JsonParseException("Expected 'true'", currentOffset);
                }
            }
        }
        
        currentOffset += 4;
        return astStore.addNode(AstStore.TYPE_BOOLEAN_TRUE, start, currentOffset);
    }
    
    private int parseFalse() throws JsonParseException {
        int start = currentOffset;
        if (currentOffset + 5 > inputLength) {
            throw new JsonParseException("Expected 'false'", currentOffset);
        }
        
        if (fastBytes != null) {
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset + currentOffset;
            for (int i = 0; i < 5; i++) {
                if (bytes[baseOffset + i] != FALSE_BYTES[i]) {
                    throw new JsonParseException("Expected 'false'", currentOffset);
                }
            }
        } else {
            for (int i = 0; i < 5; i++) {
                if (cursor.byteAt(currentOffset + i) != FALSE_BYTES[i]) {
                    throw new JsonParseException("Expected 'false'", currentOffset);
                }
            }
        }
        
        currentOffset += 5;
        return astStore.addNode(AstStore.TYPE_BOOLEAN_FALSE, start, currentOffset);
    }
    
    private int parseNull() throws JsonParseException {
        int start = currentOffset;
        if (currentOffset + 4 > inputLength) {
            throw new JsonParseException("Expected 'null'", currentOffset);
        }
        
        if (fastBytes != null) {
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset + currentOffset;
            for (int i = 0; i < 4; i++) {
                if (bytes[baseOffset + i] != NULL_BYTES[i]) {
                    throw new JsonParseException("Expected 'null'", currentOffset);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                if (cursor.byteAt(currentOffset + i) != NULL_BYTES[i]) {
                    throw new JsonParseException("Expected 'null'", currentOffset);
                }
            }
        }
        
        currentOffset += 4;
        return astStore.addNode(AstStore.TYPE_NULL, start, currentOffset);
    }
    
    private void skipWhitespace() {
        // Optimized whitespace skipping with direct byte access for maximum performance
        final int length = inputLength;
        int offset = currentOffset;
        
        if (fastBytes != null) {
            // Fast path: direct array access
            final byte[] bytes = fastBytes;
            final int baseOffset = fastBytesOffset;
            while (offset < length) {
                byte b = bytes[baseOffset + offset];
                switch (b) {
                    case CH_SPACE:
                    case CH_TAB:
                    case CH_LINEFEED:
                    case CH_CARRIAGE_RETURN:
                        offset++;
                        break;
                    default:
                        currentOffset = offset;
                        return;
                }
            }
        } else {
            // Slow path: cursor access
            while (offset < length) {
                byte b = cursor.byteAt(offset);
                switch (b) {
                    case CH_SPACE:
                    case CH_TAB:
                    case CH_LINEFEED:
                    case CH_CARRIAGE_RETURN:
                        offset++;
                        break;
                    default:
                        currentOffset = offset;
                        return;
                }
            }
        }
        currentOffset = offset;
    }
    
    private boolean isDigit(byte b) {
        return b >= CH_0 && b <= CH_9;
    }
    
    private void pushStack(int nodeIndex) {
        if (stackPointer >= stack.length) {
            // Grow stack
            int[] newStack = new int[stack.length * 2];
            System.arraycopy(stack, 0, newStack, 0, stackPointer);
            // Note: we can't reassign the final field, so we'd need to make it non-final
            // For now, we'll just throw an exception if stack overflows
            throw new JsonParseException("Stack overflow - JSON too deeply nested", currentOffset);
        }
        stack[stackPointer++] = nodeIndex;
    }
    
    private int popStack() {
        if (stackPointer == 0) {
            throw new JsonParseException("Stack underflow", currentOffset);
        }
        return stack[--stackPointer];
    }
    
}
