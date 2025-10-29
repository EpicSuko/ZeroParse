package com.suko.zeroparse.stack;

import com.suko.zeroparse.JsonType;

/**
 * Flat array-based storage for AST nodes with efficient growth strategy.
 * 
 * <p>This class stores all node data in parallel arrays for cache efficiency.
 * Each node is identified by an index, and the arrays store type, position,
 * and linkage information.</p>
 */
public final class AstStore {
    
    // Node type constants
    public static final byte TYPE_OBJECT = 1;
    public static final byte TYPE_ARRAY = 2;
    public static final byte TYPE_STRING = 3;
    public static final byte TYPE_NUMBER = 4;
    public static final byte TYPE_BOOLEAN_TRUE = 5;
    public static final byte TYPE_BOOLEAN_FALSE = 6;
    public static final byte TYPE_NULL = 7;
    public static final byte TYPE_FIELD = 8; // Object field name
    
    // Flag constants
    public static final int FLAG_STRING_ESCAPED = 0x01;
    public static final int FLAG_NUMBER_FLOAT = 0x02;
    
    // Initial capacity
    private static final int INITIAL_CAPACITY = 32;
    private static final int GROWTH_FACTOR = 2;
    
    // Node data arrays (all same length)
    private byte[] types;
    private int[] starts;
    private int[] ends;
    private int[] firstChildren;
    private int[] nextSiblings;
    private int[] flags;
    
    // Current size and capacity
    private int size;
    private int capacity;
    
    // Root node index
    private int rootIndex = -1;
    
    /**
     * Create a new AstStore with initial capacity.
     */
    public AstStore() {
        this.capacity = INITIAL_CAPACITY;
        this.types = new byte[capacity];
        this.starts = new int[capacity];
        this.ends = new int[capacity];
        this.firstChildren = new int[capacity];
        this.nextSiblings = new int[capacity];
        this.flags = new int[capacity];
        this.size = 0;
    }
    
    /**
     * Add a new node to the store.
     * 
     * @param type the node type
     * @param start the start position in the input
     * @param end the end position in the input
     * @return the node index
     */
    public int addNode(byte type, int start, int end) {
        ensureCapacity();
        
        int index = size++;
        types[index] = type;
        starts[index] = start;
        ends[index] = end;
        firstChildren[index] = -1;
        nextSiblings[index] = -1;
        flags[index] = 0;
        
        return index;
    }
    
    /**
     * Add a new node with flags.
     * 
     * @param type the node type
     * @param start the start position in the input
     * @param end the end position in the input
     * @param flags the node flags
     * @return the node index
     */
    public int addNode(byte type, int start, int end, int flags) {
        int index = addNode(type, start, end);
        this.flags[index] = flags;
        return index;
    }
    
    /**
     * Set the root node index.
     * 
     * @param rootIndex the root node index
     */
    public void setRoot(int rootIndex) {
        this.rootIndex = rootIndex;
    }
    
    /**
     * Get the root node index.
     * 
     * @return the root node index
     */
    public int getRoot() {
        return rootIndex;
    }
    
    /**
     * Get the current number of nodes.
     * 
     * @return the size
     */
    public int size() {
        return size;
    }
    
    /**
     * Get the node type.
     * 
     * @param index the node index
     * @return the node type
     */
    public byte getType(int index) {
        return types[index];
    }
    
    /**
     * Get the start position.
     * 
     * @param index the node index
     * @return the start position
     */
    public int getStart(int index) {
        return starts[index];
    }
    
    /**
     * Get the end position.
     * 
     * @param index the node index
     * @return the end position
     */
    public int getEnd(int index) {
        return ends[index];
    }
    
    /**
     * Get the first child index.
     * 
     * @param index the node index
     * @return the first child index, or -1 if none
     */
    public int getFirstChild(int index) {
        return firstChildren[index];
    }
    
    /**
     * Get the next sibling index.
     * 
     * @param index the node index
     * @return the next sibling index, or -1 if none
     */
    public int getNextSibling(int index) {
        return nextSiblings[index];
    }
    
    /**
     * Get the node flags.
     * 
     * @param index the node index
     * @return the flags
     */
    public int getFlags(int index) {
        return flags[index];
    }
    
    /**
     * Set the first child of a node.
     * 
     * @param parentIndex the parent node index
     * @param childIndex the child node index
     */
    public void setFirstChild(int parentIndex, int childIndex) {
        firstChildren[parentIndex] = childIndex;
    }
    
    /**
     * Set the next sibling of a node.
     * 
     * @param nodeIndex the node index
     * @param siblingIndex the sibling node index
     */
    public void setNextSibling(int nodeIndex, int siblingIndex) {
        nextSiblings[nodeIndex] = siblingIndex;
    }
    
    /**
     * Set the end position of a node.
     * 
     * @param nodeIndex the node index
     * @param end the end position
     */
    public void setEnd(int nodeIndex, int end) {
        ends[nodeIndex] = end;
    }
    
    /**
     * Add a child to a parent node.
     * 
     * @param parentIndex the parent node index
     * @param childIndex the child node index
     */
    public void addChild(int parentIndex, int childIndex) {
        if (firstChildren[parentIndex] == -1) {
            // First child
            firstChildren[parentIndex] = childIndex;
        } else {
            // Add as sibling to the last child
            int lastChild = firstChildren[parentIndex];
            while (nextSiblings[lastChild] != -1) {
                lastChild = nextSiblings[lastChild];
            }
            nextSiblings[lastChild] = childIndex;
        }
    }
    
    /**
     * Convert a node type to JsonType.
     * 
     * @param type the node type
     * @return the JsonType
     */
    public static JsonType toJsonType(byte type) {
        switch (type) {
            case TYPE_OBJECT: return JsonType.OBJECT;
            case TYPE_ARRAY: return JsonType.ARRAY;
            case TYPE_STRING: return JsonType.STRING;
            case TYPE_NUMBER: return JsonType.NUMBER;
            case TYPE_BOOLEAN_TRUE:
            case TYPE_BOOLEAN_FALSE: return JsonType.BOOLEAN;
            case TYPE_NULL: return JsonType.NULL;
            default: throw new IllegalArgumentException("Unknown node type: " + type);
        }
    }
    
    /**
     * Check if a node has a specific flag.
     * 
     * @param index the node index
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public boolean hasFlag(int index, int flag) {
        return (flags[index] & flag) != 0;
    }
    
    /**
     * Reset the store for reuse.
     */
    public void reset() {
        size = 0;
        rootIndex = -1;
    }
    
    private void ensureCapacity() {
        if (size >= capacity) {
            int newCapacity = capacity * GROWTH_FACTOR;
            byte[] newTypes = new byte[newCapacity];
            int[] newStarts = new int[newCapacity];
            int[] newEnds = new int[newCapacity];
            int[] newFirstChildren = new int[newCapacity];
            int[] newNextSiblings = new int[newCapacity];
            int[] newFlags = new int[newCapacity];
            
            System.arraycopy(types, 0, newTypes, 0, size);
            System.arraycopy(starts, 0, newStarts, 0, size);
            System.arraycopy(ends, 0, newEnds, 0, size);
            System.arraycopy(firstChildren, 0, newFirstChildren, 0, size);
            System.arraycopy(nextSiblings, 0, newNextSiblings, 0, size);
            System.arraycopy(flags, 0, newFlags, 0, size);
            
            this.types = newTypes;
            this.starts = newStarts;
            this.ends = newEnds;
            this.firstChildren = newFirstChildren;
            this.nextSiblings = newNextSiblings;
            this.flags = newFlags;
            this.capacity = newCapacity;
        }
    }
}
