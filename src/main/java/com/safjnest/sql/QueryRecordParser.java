package com.safjnest.sql;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

public final class QueryRecordParser {

    private QueryRecordParser() {
    }

    public static QueryRecord fromRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        QueryRecord record = new QueryRecord();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String key = metadata.getColumnLabel(index);
            if (key == null || key.isBlank()) key = metadata.getColumnName(index);
            key = key.toLowerCase(Locale.ROOT);
            Object value = resultSet.getObject(index);
            if (value instanceof Blob blob) {
                value = Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length()));
            } else if (value instanceof byte[] bytes) {
                value = Base64.getEncoder().encodeToString(bytes.clone());
            } else if (value != null) {
                value = String.valueOf(value);
            }
            record.put(key, value);
        }
        return record;
    }

    public static List<QueryRecord> fromRows(ResultSet resultSet) throws SQLException {
        List<QueryRecord> result = new ArrayList<>();
        while (resultSet.next()) result.add(fromRow(resultSet));
        return result;
    }

    public static QueryRecord fromDocument(Document document) {
        return fromMap(document);
    }

    public static List<QueryRecord> fromDocuments(Iterable<Document> documents) {
        List<QueryRecord> result = new ArrayList<>();
        if (documents != null) {
            for (Document document : documents) {
                if (document != null) result.add(fromDocument(document));
            }
        }
        return result;
    }

    public static QueryRecord fromMap(Map<?, ?> source) {
        QueryRecord record = new QueryRecord();
        if (source == null) return record;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) record.put(String.valueOf(entry.getKey()), detachedValue(entry.getValue()));
        }
        return record;
    }

    public static List<QueryRecord> fromIterable(Iterable<?> source) {
        List<QueryRecord> result = new ArrayList<>();
        if (source != null) {
            for (Object value : source) {
                if (value instanceof Map<?, ?> map) result.add(fromMap(map));
            }
        }
        return result;
    }

    public static Map<String, Object> toDetachedMap(Map<String, ?> source) {
        QueryRecord detached = fromMap(source);
        return new java.util.LinkedHashMap<>(detached);
    }

    public static Document toDocument(Map<String, ?> source) {
        Document document = new Document();
        if (source == null) return document;
        for (Map.Entry<String, ?> entry : source.entrySet()) document.put(entry.getKey(), mongoValue(entry.getValue()));
        return document;
    }

    private static Object detachedValue(Object value) {
        if (value == null) return null;
        if (value instanceof QueryRecord record) return fromMap(record);
        if (value instanceof Document document) return fromDocument(document);
        if (value instanceof Map<?, ?> map) return fromMap(map);
        if (value instanceof Iterable<?> iterable) return detachedList(iterable);
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof Date date) return Instant.ofEpochMilli(date.getTime());
        if (value instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        if (value instanceof ObjectId objectId) return objectId.toHexString();
        if (value instanceof org.bson.BsonValue bsonValue) return detachedBsonValue(bsonValue);
        if (value instanceof BigDecimal || value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Instant) return value;
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        return String.valueOf(value);
    }

    private static Object detachedList(Iterable<?> source) {
        List<Object> values = new ArrayList<>();
        for (Object value : source) values.add(detachedValue(value));
        return Collections.unmodifiableList(values);
    }

    private static Object detachedBsonValue(org.bson.BsonValue value) {
        if (value.isString()) return value.asString().getValue();
        if (value.isBoolean()) return value.asBoolean().getValue();
        if (value.isInt32()) return value.asInt32().getValue();
        if (value.isInt64()) return value.asInt64().getValue();
        if (value.isDouble()) return value.asDouble().getValue();
        if (value.isDecimal128()) return value.asDecimal128().getValue().bigDecimalValue();
        if (value.isDateTime()) return Instant.ofEpochMilli(value.asDateTime().getValue());
        if (value.isObjectId()) return value.asObjectId().getValue().toHexString();
        if (value.isDocument()) return fromMap(value.asDocument());
        if (value.isArray()) return detachedList(value.asArray().getValues());
        return value.toString();
    }

    private static Object mongoValue(Object value) {
        if (value == null) return null;
        if (value instanceof QueryRecord record) return toDocument(record);
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) values.put(String.valueOf(entry.getKey()), mongoValue(entry.getValue()));
            }
            return values;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object item : iterable) values.add(mongoValue(item));
            return values;
        }
        if (value instanceof Instant instant) return Date.from(instant);
        if (value instanceof byte[] bytes) return bytes.clone();
        return value;
    }
}
