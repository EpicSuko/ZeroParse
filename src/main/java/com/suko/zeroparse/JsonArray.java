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
    
    // AST backing (mutable for pooling)
    protected AstStore astStore;
    protected int nodeIndex;
    protected InputCursor cursor;
    
    // Optional parse context for pooled view creation
    JsonParseContext context;
    
    // Lazy element index cache
    private int[] elementIndices;
    private int elementIndicesCount;  // Valid count of indices in elementIndices array
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
    
    /**
     * Default constructor for object pooling.
     */
    JsonArray() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
    }
    
    /**
     * Reset this array for reuse from the pool.
     * Called by the pool's reset action.
     */
    void reset(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.context = null;
        this.elementIndexBuilt = false;
        this.elementIndices = null;
        this.elementIndicesCount = 0;
    }
    
    /**
     * Reset this array with a parse context for pooled sub-view creation.
     */
    void reset(AstStore astStore, int nodeIndex, InputCursor cursor, JsonParseContext context) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.context = context;
        this.elementIndexBuilt = false;
        this.elementIndices = null;
        this.elementIndicesCount = 0;
    }
    
    /**
     * Reset this array to null state (for pool reset action).
     */
    void reset() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.context = null;
        this.elementIndexBuilt = false;
        this.elementIndices = null;
        this.elementIndicesCount = 0;
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
        return elementIndicesCount;
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
        
        // Use context buffer if available (zero allocation!), otherwise use ArrayList
        if (context != null) {
            int[] buffer = context.getFieldIndexBuffer();  // Reuse the same buffer as JsonObject
            int count = 0;
            int childIndex = astStore.getFirstChild(nodeIndex);
            
            while (childIndex != -1 && count < buffer.length) {
                buffer[count++] = childIndex;
                childIndex = astStore.getNextSibling(childIndex);
            }
            
            // Use pooled array directly (no copy needed, buffer is reused)
            elementIndices = context.borrowFieldIndicesArray(count);
            System.arraycopy(buffer, 0, elementIndices, 0, count);
            elementIndicesCount = count;
        } else {
            // Fallback for non-pooled mode
            java.util.List<Integer> indices = new java.util.ArrayList<>();
            int childIndex = astStore.getFirstChild(nodeIndex);
            
            while (childIndex != -1) {
                indices.add(childIndex);
                childIndex = astStore.getNextSibling(childIndex);
            }
            
            elementIndicesCount = indices.size();
            elementIndices = new int[elementIndicesCount];
            for (int i = 0; i < elementIndicesCount; i++) {
                elementIndices[i] = indices.get(i);
            }
        }
        
        elementIndexBuilt = true;
    }
    
    private JsonValue createValueView(int valueIndex) {
        byte type = astStore.getType(valueIndex);
        
        // If we have a context, use pooled views (context is set inside borrowView for Object/Array)
        if (context != null) {
            return context.borrowView(type, astStore, valueIndex, cursor);
        }
        
        // Fallback to direct allocation (backward compatibility)
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
