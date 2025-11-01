package com.suko.zeroparse;

import com.suko.zeroparse.stack.AstStore;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A JSON object with lazy, zero-copy field access backed by an AST.
 * 
 * <p>This class uses AST-backed lazy evaluation for efficient parsing
 * without materializing all fields upfront.</p>
 */
public final class JsonObject implements JsonValue, Iterable<Map.Entry<Utf8Slice, JsonValue>> {
    
    // AST backing (mutable for pooling)
    protected AstStore astStore;
    protected int nodeIndex;
    protected InputCursor cursor;
    
    // Optional parse context for pooled view creation
    JsonParseContext context;
    
    // Lazy field index cache
    private int[] fieldIndices;
    private boolean fieldIndexBuilt = false;
    
    /**
     * ThreadLocal cache for field name byte arrays to avoid repeated String.getBytes() calls.
     * Uses a simple LRU-style cache with 8 slots (covers most common field names).
     */
    private static final ThreadLocal<FieldNameCache> FIELD_NAME_CACHE = 
        ThreadLocal.withInitial(FieldNameCache::new);
    
    /**
     * Simple cache for field name byte arrays (avoids String.getBytes overhead).
     */
    private static final class FieldNameCache {
        private static final int CACHE_SIZE = 8;
        private final String[] keys = new String[CACHE_SIZE];
        private final byte[][] values = new byte[CACHE_SIZE][];
        private int nextSlot = 0;
        
        byte[] getBytes(String name) {
            // Fast path: check cache
            for (int i = 0; i < CACHE_SIZE; i++) {
                if (name.equals(keys[i])) {
                    return values[i];
                }
            }
            
            // Cache miss: compute and store
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            keys[nextSlot] = name;
            values[nextSlot] = bytes;
            nextSlot = (nextSlot + 1) % CACHE_SIZE;
            return bytes;
        }
    }
    
    /**
     * Create a new JsonObject backed by AST.
     * 
     * @param astStore the AST store
     * @param nodeIndex the node index
     * @param cursor the input cursor
     */
    public JsonObject(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
    }
    
    /**
     * Default constructor for object pooling.
     */
    JsonObject() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
    }
    
    /**
     * Reset this object for reuse from the pool.
     * Called by the pool's reset action.
     */
    void reset(AstStore astStore, int nodeIndex, InputCursor cursor) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.context = null;
        this.fieldIndexBuilt = false;
        this.fieldIndices = null;
    }
    
    /**
     * Reset this object with a parse context for pooled sub-view creation.
     */
    void reset(AstStore astStore, int nodeIndex, InputCursor cursor, JsonParseContext context) {
        this.astStore = astStore;
        this.nodeIndex = nodeIndex;
        this.cursor = cursor;
        this.context = context;
        this.fieldIndexBuilt = false;
        this.fieldIndices = null;
    }
    
    /**
     * Reset this object to null state (for pool reset action).
     */
    void reset() {
        this.astStore = null;
        this.nodeIndex = 0;
        this.cursor = null;
        this.context = null;
        this.fieldIndexBuilt = false;
        this.fieldIndices = null;
    }
    
    @Override
    public JsonType getType() {
        return JsonType.OBJECT;
    }
    
    @Override
    public JsonObject asObject() {
        return this;
    }
    
    public int size() {
        buildFieldIndex();
        return fieldIndices.length;
    }
    
    public boolean isEmpty() {
        return astStore.getFirstChild(nodeIndex) == -1;
    }
    
    public JsonValue get(String name) {
        if (name == null) {
            return null;
        }
        
        // Use cached byte array to avoid repeated String.getBytes() calls
        byte[] nameBytes = FIELD_NAME_CACHE.get().getBytes(name);
        Utf8Slice nameSlice = Utf8Slice.temporary(nameBytes, 0, nameBytes.length);
        return get(nameSlice);
    }
    
    public JsonValue get(Utf8Slice name) {
        if (name == null) {
            return null;
        }
        
        int childIndex = astStore.getFirstChild(nodeIndex);
        while (childIndex != -1) {
            // Each field has two children: name and value
            int nameIndex = astStore.getFirstChild(childIndex);
            int valueIndex = astStore.getNextSibling(nameIndex);
            
            if (nameIndex != -1 && astStore.getType(nameIndex) == AstStore.TYPE_STRING) {
                Utf8Slice fieldName = createFieldNameSlice(nameIndex);
                if (fieldName.equals(name)) {
                    return createValueView(valueIndex);
                }
            }
            
            childIndex = astStore.getNextSibling(childIndex);
        }
        
        return null;
    }
    
    public JsonStringView getStringView(String name) {
        JsonValue value = get(name);
        return value != null && value.isString() ? value.asString() : null;
    }
    
    public JsonNumberView getNumberView(String name) {
        JsonValue value = get(name);
        return value != null && value.isNumber() ? value.asNumber() : null;
    }
    
    public JsonBoolean getBoolean(String name) {
        JsonValue value = get(name);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }
    
    public JsonObject getObject(String name) {
        JsonValue value = get(name);
        return value != null && value.isObject() ? value.asObject() : null;
    }
    
    public JsonArray getArray(String name) {
        JsonValue value = get(name);
        return value != null && value.isArray() ? value.asArray() : null;
    }
    
    public boolean has(String name) {
        return get(name) != null;
    }
    
    public boolean has(Utf8Slice name) {
        return get(name) != null;
    }
    
    @Override
    public Iterator<Map.Entry<Utf8Slice, JsonValue>> iterator() {
        return new FieldIterator();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        JsonObject other = (JsonObject) obj;
        if (nodeIndex != other.nodeIndex || astStore != other.astStore) {
            return false;
        }
        
        // Compare field by field
        int childIndex = astStore.getFirstChild(nodeIndex);
        int otherChildIndex = other.astStore.getFirstChild(other.nodeIndex);
        
        while (childIndex != -1 && otherChildIndex != -1) {
            if (!compareField(childIndex, otherChildIndex)) {
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
        sb.append('{');
        
        int childIndex = astStore.getFirstChild(nodeIndex);
        boolean first = true;
        
        while (childIndex != -1) {
            if (!first) {
                sb.append(',');
            }
            
            // Field name
            int nameIndex = astStore.getFirstChild(childIndex);
            if (nameIndex != -1) {
                sb.append('"').append(createFieldNameSlice(nameIndex).toString()).append('"');
                sb.append(':');
                
                // Field value
                int valueIndex = astStore.getNextSibling(nameIndex);
                if (valueIndex != -1) {
                    JsonValue value = createValueView(valueIndex);
                    if (value.isString()) {
                        sb.append('"').append(value.toString()).append('"');
                    } else {
                        sb.append(value.toString());
                    }
                }
            }
            
            childIndex = astStore.getNextSibling(childIndex);
            first = false;
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    private void buildFieldIndex() {
        if (fieldIndexBuilt) {
            return;
        }
        
        java.util.List<Integer> indices = new java.util.ArrayList<>();
        int childIndex = astStore.getFirstChild(nodeIndex);
        
        while (childIndex != -1) {
            indices.add(childIndex);
            childIndex = astStore.getNextSibling(childIndex);
        }
        
        fieldIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            fieldIndices[i] = indices.get(i);
        }
        
        fieldIndexBuilt = true;
    }
    
    private Utf8Slice createFieldNameSlice(int nameIndex) {
        int start = astStore.getStart(nameIndex);
        int length = astStore.getEnd(nameIndex) - start;
        return cursor.slice(start, length);
    }
    
    private JsonValue createValueView(int valueIndex) {
        byte type = astStore.getType(valueIndex);
        
        // If we have a context, use pooled views
        if (context != null) {
            JsonValue view = context.borrowView(type, astStore, valueIndex, cursor);
            // Pass context to nested objects/arrays for recursive pooling
            if (view instanceof JsonObject) {
                ((JsonObject) view).context = context;
            } else if (view instanceof JsonArray) {
                ((JsonArray) view).context = context;
            }
            return view;
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
    
    private boolean compareField(int fieldIndex1, int fieldIndex2) {
        // Compare field names
        int nameIndex1 = astStore.getFirstChild(fieldIndex1);
        int nameIndex2 = astStore.getFirstChild(fieldIndex2);
        
        if (nameIndex1 == -1 || nameIndex2 == -1) {
            return nameIndex1 == nameIndex2;
        }
        
        Utf8Slice name1 = createFieldNameSlice(nameIndex1);
        Utf8Slice name2 = createFieldNameSlice(nameIndex2);
        
        if (!name1.equals(name2)) {
            return false;
        }
        
        // Compare field values
        int valueIndex1 = astStore.getNextSibling(nameIndex1);
        int valueIndex2 = astStore.getNextSibling(nameIndex2);
        
        if (valueIndex1 == -1 || valueIndex2 == -1) {
            return valueIndex1 == valueIndex2;
        }
        
        JsonValue value1 = createValueView(valueIndex1);
        JsonValue value2 = createValueView(valueIndex2);
        
        return value1.equals(value2);
    }
    
    private class FieldIterator implements Iterator<Map.Entry<Utf8Slice, JsonValue>> {
        private int childIndex = astStore.getFirstChild(nodeIndex);
        
        @Override
        public boolean hasNext() {
            return childIndex != -1;
        }
        
        @Override
        public Map.Entry<Utf8Slice, JsonValue> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            
            int nameIndex = astStore.getFirstChild(childIndex);
            int valueIndex = astStore.getNextSibling(nameIndex);
            
            Utf8Slice name = createFieldNameSlice(nameIndex);
            JsonValue value = createValueView(valueIndex);
            
            childIndex = astStore.getNextSibling(childIndex);
            
            return new Map.Entry<Utf8Slice, JsonValue>() {
                @Override
                public Utf8Slice getKey() {
                    return name;
                }
                
                @Override
                public JsonValue getValue() {
                    return value;
                }
                
                @Override
                public JsonValue setValue(JsonValue value) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}

