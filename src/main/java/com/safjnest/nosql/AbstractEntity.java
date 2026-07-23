package com.safjnest.nosql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class AbstractEntity<E extends AbstractEntity<E>> {

    @JsonIgnore
    private final PendingChanges pendingChanges = new PendingChanges();
    @JsonIgnore
    private final Map<String, Object> filters = new LinkedHashMap<>();
    @JsonIgnore
    private boolean instant;
    @JsonIgnore
    private boolean existing;

    @SuppressWarnings("unchecked")
    public final E instant() {
        instant = true;
        return (E) this;
    }

    @SuppressWarnings("unchecked")
    public final E deferred() {
        instant = false;
        return (E) this;
    }

    public final boolean isDirty() {
        return !pendingChanges.isEmpty();
    }

    public final boolean update() {
        return flush(false);
    }

    public final boolean updateNow() {
        return flush(false);
    }

    public final boolean upsert() {
        return flush(true);
    }

    protected final void setValue(String path, Object value) {
        pendingChanges.add("set", path, attributes("value", copyValue(value)));
        flushIfInstant();
    }

    protected final void unsetValue(String path) {
        pendingChanges.add("unset", path, Map.of());
        flushIfInstant();
    }

    protected final void pushValue(String path, Object value) {
        pendingChanges.add("push", path, attributes("value", copyValue(value)));
        flushIfInstant();
    }

    protected final void pullValue(String path, Object value) {
        pendingChanges.add("pullValue", path, attributes("value", copyValue(value)));
        flushIfInstant();
    }

    protected final void removeArrayElement(String arrayPath, String keyField, Object keyValue) {
        pendingChanges.add("pull", arrayPath, attributes(
                "keyField", keyField,
                "keyValue", copyValue(keyValue)));
        flushIfInstant();
    }

    protected final void replaceArrayElement(
            String arrayPath,
            String keyField,
            Object keyValue,
            Object replacement) {
        pendingChanges.add("replaceArrayElement", arrayPath, attributes(
                "keyField", keyField,
                "keyValue", copyValue(keyValue),
                "value", copyValue(replacement)));
        flushIfInstant();
    }

    protected final void setArrayElementField(
            String arrayPath,
            String keyField,
            Object keyValue,
            String targetField,
            Object value) {
        pendingChanges.add("setArrayElementField", arrayPath, attributes(
                "keyField", keyField,
                "keyValue", copyValue(keyValue),
                "targetField", targetField,
                "value", copyValue(value)));
        flushIfInstant();
    }

    protected final void replaceOrAppendArrayElement(
            String arrayPath,
            String keyField,
            Object keyValue,
            Object replacement) {
        pendingChanges.add("replaceOrAppendArrayElement", arrayPath, attributes(
                "keyField", keyField,
                "keyValue", copyValue(keyValue),
                "value", copyValue(replacement)));
        flushIfInstant();
    }

    protected final void filterValue(String path, Object value) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Entity filter path is required");
        filters.put(path, copyValue(value));
    }

    protected final void markExisting() {
        existing = true;
        pendingChanges.clear();
    }

    protected final boolean isExisting() {
        return existing;
    }

    protected Map<String, Object> snapshotValues() {
        return Map.of();
    }

    protected abstract String collectionName();

    protected abstract String entityId();

    final List<Map<String, Object>> pendingOperations(boolean includeSnapshot) {
        List<Map<String, Object>> operations = new ArrayList<>();
        if (includeSnapshot) {
            for (Map.Entry<String, Object> entry : snapshotValues().entrySet()) {
                operations.add(operation("set", entry.getKey(), attributes("value", copyValue(entry.getValue()))));
            }
        }
        operations.addAll(pendingChanges.snapshot());
        return operations;
    }

    final Map<String, Object> persistenceFilters() {
        return new LinkedHashMap<>(filters);
    }

    private boolean flush(boolean upsert) {
        boolean includeSnapshot = upsert && !existing;
        List<Map<String, Object>> operations = pendingOperations(includeSnapshot);
        if (operations.isEmpty()) return true;

        boolean updated = NoSqlEntityExecutor.execute(collectionName(), entityId(), operations, persistenceFilters(), upsert);
        if (updated) {
            existing = true;
            pendingChanges.clear();
            filters.clear();
        }
        return updated;
    }

    private void flushIfInstant() {
        if (instant && !flush(false)) {
            throw new IllegalStateException("Unable to persist " + getClass().getName() + " id=" + entityId());
        }
    }

    private static Map<String, Object> operation(String type, String path, Map<String, Object> values) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("type", type);
        operation.put("path", path);
        operation.putAll(values);
        return operation;
    }

    private static Map<String, Object> attributes(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> source) {
            List<Object> copy = new ArrayList<>(source.size());
            for (Object item : source) copy.add(copyValue(item));
            return copy;
        }
        if (value instanceof byte[] bytes) return bytes.clone();
        return value;
    }

    private static final class PendingChanges {
        private final List<Map<String, Object>> operations = new ArrayList<>();

        private void add(String type, String path, Map<String, Object> values) {
            operations.add(operation(type, path, values));
        }

        private boolean isEmpty() {
            return operations.isEmpty();
        }

        private List<Map<String, Object>> snapshot() {
            List<Map<String, Object>> copy = new ArrayList<>(operations.size());
            for (Map<String, Object> operation : operations) {
                copy.add(castMap(copyValue(operation)));
            }
            return copy;
        }

        private void clear() {
            operations.clear();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> castMap(Object value) {
            return (Map<String, Object>) value;
        }
    }
}
