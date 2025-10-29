package com.suko.zeroparse;

import com.suko.zeroparse.stack.AstStore;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A JSON array with lazy, zero-copy element access backed by an AST.
 * 
 * <p>This class uses AST-backed lazy evaluation for efficient parsing
 * without materializing all elements upfront.</p>
 */
public final class JsonArray implements JsonValue, Iterable<JsonValue> {
    
    // AST backing
    protected final AstStore astStore;
    protected final int nodeIndex;
    protected final InputCursor cursor;
    
    // Lazy element index cache
    private int[] elementIndices;
    private boolean elementIndexBuilt = false;
    
    /**
     * Create a new JsonArray backed by AST.
     * 
     * @param astStore the AST store
     * @param nodeIndex the node index
     * @param cursor the input cursor
     */
    public JsonArray(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
    }
    
    @Override
    public JsonType getType() {
        return JsonType.ARRAY;
    }
    
    @Override
    public JsonArray asArray() {
        return this;
    }
    
    public int size() {
        buildElementIndex();
        return elementIndices.length;
    }
    
    public boolean isEmpty() {
        return astStore.getFirstChild(nodeIndex) == -1;
    }
    
    public JsonValue get(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }
        
        buildElementIndex();
        
        if (index >= elementIndices.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + elementIndices.length);
        }
        
        int elementIndex = elementIndices[index];
        return createValueView(elementIndex);
    }
    
    public JsonStringView getStringView(int index) {
        JsonValue value = get(index);
        return value != null && value.isString() ? value.asString() : null;
    }
    
    public JsonNumberView getNumberView(int index) {
        JsonValue value = get(index);
        return value != null && value.isNumber() ? value.asNumber() : null;
    }
    
    public JsonBoolean getBoolean(int index) {
        JsonValue value = get(index);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }
    
    public JsonObject getObject(int index) {
        JsonValue value = get(index);
        return value != null && value.isObject() ? value.asObject() : null;
    }
    
    public JsonArray getArray(int index) {
        JsonValue value = get(index);
        return value != null && value.isArray() ? value.asArray() : null;
    }
    
    @Override
    public Iterator<JsonValue> iterator() {
        return new ElementIterator();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        JsonArray other = (JsonArray) obj;
        if (nodeIndex != other.nodeIndex || astStore != other.astStore) {
            return false;
        }
        
        // Compare element by element
        int childIndex = astStore.getFirstChild(nodeIndex);
        int otherChildIndex = other.astStore.getFirstChild(other.nodeIndex);
        
        while (childIndex != -1 && otherChildIndex != -1) {
            JsonValue value1 = createValueView(childIndex);
            JsonValue value2 = other.createValueView(otherChildIndex);
            
            if (!value1.equals(value2)) {
                return false;
            }
            
            childIndex = astStore.getNextSibling(childIndex);
            otherChildIndex = other.astStore.getNextSibling(otherChildIndex);
        }
        
        return childIndex == -1 && otherChildIndex == -1;
    }
    
    @Override
    public int hashCode() {
        int result = 1;
        int childIndex = astStore.getFirstChild(nodeIndex);
        while (childIndex != -1) {
            result = 31 * result + childIndex;
            childIndex = astStore.getNextSibling(childIndex);
        }
        return result;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        
        int childIndex = astStore.getFirstChild(nodeIndex);
        boolean first = true;
        
        while (childIndex != -1) {
            if (!first) {
                sb.append(',');
            }
            
            JsonValue value = createValueView(childIndex);
            sb.append(value.toString());
            
            childIndex = astStore.getNextSibling(childIndex);
            first = false;
        }
        
        sb.append(']');
        return sb.toString();
    }
    
    private void buildElementIndex() {
        if (elementIndexBuilt) {
            return;
        }
        
        java.util.List<Integer> indices = new java.util.ArrayList<>();
        int childIndex = astStore.getFirstChild(nodeIndex);
        
        while (childIndex != -1) {
            indices.add(childIndex);
            childIndex = astStore.getNextSibling(childIndex);
        }
        
        elementIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            elementIndices[i] = indices.get(i);
        }
        
        elementIndexBuilt = true;
    }
    
    private JsonValue createValueView(int valueIndex) {
        byte type = astStore.getType(valueIndex);
        
        switch (type) {
            case AstStore.TYPE_OBJECT:
                return new JsonObject(astStore, valueIndex, cursor);
            case AstStore.TYPE_ARRAY:
                return new JsonArray(astStore, valueIndex, cursor);
            case AstStore.TYPE_STRING:
                return new JsonStringView(astStore, valueIndex, cursor);
            case AstStore.TYPE_NUMBER:
                return new JsonNumberView(astStore, valueIndex, cursor);
            case AstStore.TYPE_BOOLEAN_TRUE:
                return JsonBoolean.TRUE;
            case AstStore.TYPE_BOOLEAN_FALSE:
                return JsonBoolean.FALSE;
            case AstStore.TYPE_NULL:
                return JsonNull.INSTANCE;
            default:
                throw new IllegalStateException("Unknown node type: " + type);
        }
    }
    
    private class ElementIterator implements Iterator<JsonValue> {
        private int childIndex = astStore.getFirstChild(nodeIndex);
        
        @Override
        public boolean hasNext() {
            return childIndex != -1;
        }
        
        @Override
        public JsonValue next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            
            JsonValue value = createValueView(childIndex);
            childIndex = astStore.getNextSibling(childIndex);
            return value;
        }
    }
}
