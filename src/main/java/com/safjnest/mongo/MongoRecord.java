package com.safjnest.mongo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Decimal128;

public final class MongoRecord {

    private final Document document;
    private final String collection;
    private final Object id;
    private final String fieldPrefix;

    public MongoRecord(Document document) {
        this(null, documentId(document), document);
    }

    public MongoRecord(String collection, Document document) {
        this(collection, documentId(document), document);
    }

    public MongoRecord(String collection, Object id, Document document) {
        this(collection, id, document, "");
    }


    public String getCollection() {
        return collection;
    }

    public Object getId() {
        return id;
    }

    public Document toDocument() {
        return copyDocument(document);
    }

    public String get(String field) {
        Object value = value(field);
        return value == null ? null : value instanceof String string ? string : String.valueOf(value);
    }

    public String getAsString(String field) {
        Object value = value(field);
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof BsonString string) {
            return string.getValue();
        }
        throw conversion(field, String.class.getName(), value, null);
    }

    public int getAsInt(String field) {
        Object value = value(field);
        if (value == null) {
            return 0;
        }
        try {
            return numericValue(value, field).intValueExact();
        } catch (RuntimeException exception) {
            throw conversion(field, Integer.class.getName(), value, exception);
        }
    }

    public long getAsLong(String field) {
        Object value = value(field);
        if (value == null) {
            return 0L;
        }
        try {
            return numericValue(value, field).longValueExact();
        } catch (RuntimeException exception) {
            throw conversion(field, Long.class.getName(), value, exception);
        }
    }

    public double getAsDouble(String field) {
        Object value = value(field);
        if (value == null) {
            return 0D;
        }
        try {
            double result = numericValue(value, field).doubleValue();
            if (!Double.isFinite(result)) {
                throw new NumberFormatException("non-finite number");
            }
            return result;
        } catch (RuntimeException exception) {
            throw conversion(field, Double.class.getName(), value, exception);
        }
    }

    public boolean getAsBoolean(String field) {
        Object value = value(field);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof BsonBoolean booleanValue) {
            return booleanValue.getValue();
        }
        throw conversion(field, Boolean.class.getName(), value, null);
    }

    public Instant getAsInstant(String field) {
        Object value = value(field);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Instant instant) {
                return instant;
            }
            if (value instanceof Date date) {
                return date.toInstant();
            }
            if (value instanceof BsonDateTime dateTime) {
                return Instant.ofEpochMilli(dateTime.getValue());
            }
            if (value instanceof Number || value instanceof String string && isInteger(string)) {
                return Instant.ofEpochMilli(numericValue(value, field).longValueExact());
            }
            if (value instanceof String string) {
                return Instant.parse(string.trim());
            }
        } catch (DateTimeParseException | ArithmeticException | NumberFormatException exception) {
            throw conversion(field, Instant.class.getName(), value, exception);
        }
        throw conversion(field, Instant.class.getName(), value, null);
    }

    public <E extends Enum<E>> E getAsEnum(String field, Class<E> type) {
        Objects.requireNonNull(type, "type");
        Object value = value(field);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        String name;
        if (value instanceof String string) {
            name = string;
        } else if (value instanceof BsonString string) {
            name = string.getValue();
        } else {
            throw conversion(field, type.getName(), value, null);
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            throw conversion(field, type.getName(), value, exception);
        }
    }

    public MongoRecord getAsRecord(String field) {
        Object value = value(field);
        if (value == null) {
            return null;
        }
        return nestedRecord(field, value);
    }

    public List<MongoRecord> getAsRecords(String field) {
        Object value = value(field);
        if (value == null) {
            return List.of();
        }
        List<?> values = listValue(field, value);
        List<MongoRecord> records = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object item = values.get(index);
            records.add(item == null ? null : nestedRecord(field + "[" + index + "]", item));
        }
        return unmodifiable(records);
    }

    public <T> List<T> getAsList(String field, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = value(field);
        if (value == null) {
            return List.of();
        }
        List<?> values = listValue(field, value);
        List<T> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(convertValue(values.get(index), type, field + "[" + index + "]"));
        }
        return unmodifiable(result);
    }

    public <T> T getAs(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return MongoDB.read(this, type);
    }

    public <T> T getAs(String field, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return convertValue(value(field), type, fieldPath(field));
    }

    // -------------------------------------------------------------------------

    private MongoRecord(
            String collection,
            Object id,
            Document document,
            String fieldPrefix) {
        this.document = copyDocument(Objects.requireNonNull(document, "document"));
        this.collection = collection;
        this.id = id == null ? this.document.get("_id") : id;
        this.fieldPrefix = fieldPrefix;
    }

    private Object value(String field) {
        Objects.requireNonNull(field, "field");
        return document.get(field);
    }

    private <T> T convertValue(Object value, Class<T> type, String field) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return type.cast(convertString(value, field));
        }
        if (type == Integer.class) {
            return type.cast(convertInt(value, field));
        }
        if (type == Long.class) {
            return type.cast(convertLong(value, field));
        }
        if (type == Double.class) {
            return type.cast(convertDouble(value, field));
        }
        if (type == Boolean.class) {
            return type.cast(convertBoolean(value, field));
        }
        if (type == Instant.class) {
            return type.cast(convertInstant(value, field));
        }
        if (type == MongoRecord.class) {
            return type.cast(nestedRecord(field, value));
        }
        if (type.isEnum()) {
            return type.cast(convertEnum(value, type, field));
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (isDocumentValue(value)) {
            return MongoDB.read(nestedRecord(field, value), type);
        }
        throw conversion(field, type.getName(), value, null);
    }

    private String convertString(Object value, String field) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof BsonString string) {
            return string.getValue();
        }
        throw conversion(field, String.class.getName(), value, null);
    }

    private Integer convertInt(Object value, String field) {
        try {
            return numericValue(value, field).intValueExact();
        } catch (RuntimeException exception) {
            throw conversion(field, Integer.class.getName(), value, exception);
        }
    }

    private Long convertLong(Object value, String field) {
        try {
            return numericValue(value, field).longValueExact();
        } catch (RuntimeException exception) {
            throw conversion(field, Long.class.getName(), value, exception);
        }
    }

    private Double convertDouble(Object value, String field) {
        try {
            double result = numericValue(value, field).doubleValue();
            if (!Double.isFinite(result)) {
                throw new NumberFormatException("non-finite number");
            }
            return result;
        } catch (RuntimeException exception) {
            throw conversion(field, Double.class.getName(), value, exception);
        }
    }

    private Boolean convertBoolean(Object value, String field) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof BsonBoolean booleanValue) {
            return booleanValue.getValue();
        }
        throw conversion(field, Boolean.class.getName(), value, null);
    }

    private Instant convertInstant(Object value, String field) {
        try {
            if (value instanceof Instant instant) {
                return instant;
            }
            if (value instanceof Date date) {
                return date.toInstant();
            }
            if (value instanceof BsonDateTime dateTime) {
                return Instant.ofEpochMilli(dateTime.getValue());
            }
            if (value instanceof Number || value instanceof String string && isInteger(string)) {
                return Instant.ofEpochMilli(numericValue(value, field).longValueExact());
            }
            if (value instanceof String string) {
                return Instant.parse(string.trim());
            }
        } catch (DateTimeParseException | ArithmeticException | NumberFormatException exception) {
            throw conversion(field, Instant.class.getName(), value, exception);
        }
        throw conversion(field, Instant.class.getName(), value, null);
    }

    private Enum<?> convertEnum(Object value, Class<?> type, String field) {
        if (type.isInstance(value)) {
            return (Enum<?>) value;
        }
        String name;
        if (value instanceof String string) {
            name = string;
        } else if (value instanceof BsonString string) {
            name = string.getValue();
        } else {
            throw conversion(field, type.getName(), value, null);
        }
        try {
            return Enum.valueOf(enumType(type), name);
        } catch (IllegalArgumentException exception) {
            throw conversion(field, type.getName(), value, exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<? extends Enum> enumType(Class<?> type) {
        return (Class<? extends Enum>) type;
    }

    private List<?> listValue(String field, Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof BsonArray array) {
            Object decoded = decodeBsonValue(array);
            if (decoded instanceof List<?> list) {
                return list;
            }
        }
        throw conversion(field, List.class.getName(), value, null);
    }

    private MongoRecord nestedRecord(String field, Object value) {
        Document nested = asDocument(field, value);
        return new MongoRecord(collection, id, nested, fieldPath(field));
    }

    private Document asDocument(String field, Object value) {
        if (value instanceof Document nested) {
            return nested;
        }
        if (value instanceof BsonDocument nested) {
            return decodeBsonDocument(nested);
        }
        if (value instanceof Map<?, ?> map) {
            Document nested = new Document();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw conversion(field, Document.class.getName(), value, null);
                }
                nested.put(key, copyValue(entry.getValue()));
            }
            return nested;
        }
        throw conversion(field, Document.class.getName(), value, null);
    }

    private boolean isDocumentValue(Object value) {
        return value instanceof Document || value instanceof BsonDocument || value instanceof Map<?, ?>;
    }

    private BigDecimal numericValue(Object value, String field) {
        try {
            if (value instanceof Decimal128 decimal128) {
                if (!decimal128.isFinite()) {
                    throw new NumberFormatException("non-finite decimal");
                }
                return decimal128.bigDecimalValue();
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return BigDecimal.valueOf(((Number) value).longValue());
            }
            if (value instanceof Float || value instanceof Double) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number)) {
                    throw new NumberFormatException("non-finite number");
                }
                return BigDecimal.valueOf(number);
            }
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (value instanceof String string) {
                return new BigDecimal(string.trim());
            }
        } catch (NumberFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NumberFormatException(exception.getMessage());
        }
        throw conversion(field, Number.class.getName(), value, null);
    }

    private boolean isInteger(String value) {
        try {
            new BigDecimal(value.trim()).longValueExact();
            return true;
        } catch (NumberFormatException | ArithmeticException exception) {
            return false;
        }
    }

    private ConversionException conversion(String field, String expected, Object value, Throwable cause) {
        return conversionException(fieldPath(field), expected, value, cause);
    }

    ConversionException conversionException(String field, String expected, Object value, Throwable cause) {
        return new ConversionException(collection, id, field, expected, value, cause);
    }

    String documentField() {
        return fieldPrefix.isEmpty() ? "<document>" : fieldPrefix;
    }

    private String fieldPath(String field) {
        return fieldPrefix.isEmpty() ? field : fieldPrefix + "." + field;
    }

    private static Object documentId(Document document) {
        return Objects.requireNonNull(document, "document").get("_id");
    }

    private static Document copyDocument(Document source) {
        Document copy = new Document();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Document document) {
            return copyDocument(document);
        }
        if (value instanceof BsonDocument document) {
            return document.clone();
        }
        if (value instanceof BsonArray array) {
            return array.clone();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        return value;
    }

    private static Document decodeBsonDocument(BsonDocument document) {
        return new DocumentCodec().decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static Object decodeBsonValue(org.bson.BsonValue value) {
        return decodeBsonDocument(new BsonDocument("value", value)).get("value");
    }

    private static <T> List<T> unmodifiable(List<T> values) {
        return Collections.unmodifiableList(values);
    }

    public static final class ConversionException extends IllegalArgumentException {

        private final String collection;
        private final Object id;
        private final String field;
        private final String expectedType;
        private final String actualType;

        private ConversionException(String collection, Object id, String field, String expectedType, Object actualValue, Throwable cause) {
            super(message(collection, id, field, expectedType, actualValue), cause);
            this.collection = collection;
            this.id = id;
            this.field = field;
            this.expectedType = expectedType;
            this.actualType = actualValue == null ? null : actualValue.getClass().getName();
        }

        public String getCollection() { return collection; }
        public Object getId() { return id; }
        public String getField() { return field; }
        public String getExpectedType() { return expectedType; }
        public String getActualType() { return actualType; }

        private static String message(String collection, Object id, String field, String expectedType, Object actualValue) {
            String actual = actualValue == null ? "null" : actualValue.getClass().getName();
            return "Mongo conversion failed [collection=" + context(collection) + ", id=" + context(id) + ", field=" + context(field) + ", expected=" + context(expectedType) + ", actual=" + actual + "]";
        }

        private static String context(Object value) {
            return value == null || value.toString().isBlank() ? "<unknown>" : value.toString();
        }
    }
}
