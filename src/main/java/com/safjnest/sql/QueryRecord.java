package com.safjnest.sql;

import java.sql.Blob;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.rowset.serial.SerialBlob;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class QueryRecord extends HashMap<String, Object> {

    private static final DateTimeFormatter BASE_FORMATTER =
        new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    public QueryRecord() {
        super();
    }

    /**
     * Keeps the old flat QueryRecord API source-compatible for callers that
     * use string columns directly. Structured values must use getValue or one
     * of the nested accessors below.
     */
    public String get(String columnName) {
        return stringValue(super.get(columnName));
    }

    public Object getValue(String columnName) {
        return super.get(columnName);
    }

    public String getAsString(String columnName) {
        return stringValue(super.get(columnName));
    }

    public <E extends Enum<E>> E getAsEnum(String columnName, Class<E> type) {
        Objects.requireNonNull(type, "type");
        Object value = super.get(columnName);
        if (value == null) return null;
        if (type.isInstance(value)) return type.cast(value);
        try {
            return Enum.valueOf(type, stringValue(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public QueryRecord getAsRecord(String columnName) {
        Object value = super.get(columnName);
        if (value instanceof QueryRecord record) {
            return record;
        }
        if (value instanceof Map<?, ?> map) {
            return QueryRecordParser.fromMap(map);
        }
        return null;
    }

    public List<QueryRecord> getAsRecords(String columnName) {
        Object value = super.get(columnName);
        return value instanceof Iterable<?> iterable ? QueryRecordParser.fromIterable(iterable) : List.of();
    }

    public <T> List<T> getAsList(String columnName, Class<T> type) {
        Object value = super.get(columnName);
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(convertValue(item, type));
        }
        return Collections.unmodifiableList(result);
    }

    public int getAsInt(String columnName){
        try {
            Object value = super.get(columnName);
            return value instanceof Number number ? number.intValue() : Integer.parseInt(stringValue(value));
        } catch (Exception e) {
            return 0;
        }
    }

    public long getAsLong(String columnName){
        try {
            Object value = super.get(columnName);
            return value instanceof Number number ? number.longValue() : Long.parseLong(stringValue(value));
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean getAsBoolean(String columnName){
        Object value = super.get(columnName);
        return value instanceof Boolean booleanValue
                ? booleanValue
                : "1".equals(stringValue(value)) || "true".equalsIgnoreCase(stringValue(value));
    }

    public double getAsDouble(String columnName) {
        try {
            Object value = super.get(columnName);
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(stringValue(value));
        } catch (Exception e) {
            return 0;
        }
    }

    public long getAsEpochSecond(String columnName) {
        try {
            Object value = super.get(columnName);
            if (value instanceof Instant instant) return instant.getEpochSecond();
            if (value instanceof Number number) return number.longValue() / 1000L;
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .optionalStart().appendFraction(ChronoField.MICRO_OF_SECOND, 1, 6, true).optionalEnd()
                .toFormatter();
            return LocalDateTime.parse(stringValue(value), formatter).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }

    public Timestamp getAsTimestamp(String columnName){
        try {
            Object value = super.get(columnName);
            if (value instanceof Timestamp timestamp) return timestamp;
            if (value instanceof Date date) return new Timestamp(date.getTime());
            if (value instanceof Instant instant) return Timestamp.from(instant);
            return Timestamp.valueOf(LocalDateTime.parse(stringValue(value), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (Exception e) {
            return null;
        }
    }

    public Date getAsDate(String columnName){
        try {
            Object value = super.get(columnName);
            if (value instanceof Date date) return date;
            if (value instanceof Instant instant) return new Date(instant.toEpochMilli());
            return Date.valueOf(LocalDateTime.parse(stringValue(value), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate());
        } catch (Exception e) {
            return null;
        }
    }

    public Instant getAsInstant(String columnName) {
        Object value = super.get(columnName);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value instanceof Number number) return Instant.ofEpochMilli(number.longValue());
        try {
            return Instant.parse(stringValue(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public Blob getAsBlob(String columnName) {
        Object value = super.get(columnName);
        if (value == null) value = super.get(columnName.toLowerCase());
        String encoded = stringValue(value);
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return new SerialBlob(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean emptyValues() {
        for (Object value : values()) {
            if (value != null && !stringValue(value).isEmpty()) return false;
        }
        return true;
    }

    public String[] toArray() {
        String[] array = new String[size()];
        int index = 0;
        for (Object value : values()) array[index++] = stringValue(value);
        return array;
    }

    public Map<String, String> getAsMap() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : entrySet()) result.put(entry.getKey(), stringValue(entry.getValue()));
        return result;
    }

    public Map<String, Object> getAsObjectMap() {
        return QueryRecordParser.toDetachedMap(this);
    }

    public LocalDateTime getAsLocalDateTime(String dateTimeStr) {
        String cleaned = dateTimeStr.trim();
        if (cleaned.contains(".")) {
            String[] parts = cleaned.split("\\.", 2);
            String base = parts[0];
            String fraction = parts[1].replaceAll("\\D.*", "");
            if (fraction.length() > 9) fraction = fraction.substring(0, 9);
            cleaned = fraction.isEmpty() ? base : base + "." + fraction;
        }
        return LocalDateTime.parse(cleaned, BASE_FORMATTER);
    }

    public LaneType getAsLaneType(String columnName) {
        try { return LaneType.valueOf(stringValue(super.get(columnName))); }
        catch (Exception e) { return null; }
    }

    public GameQueueType getAsGameQueueType(String columnName) {
        try { return GameQueueType.valueOf(stringValue(super.get(columnName))); }
        catch (Exception e) { return null; }
    }

    public TeamType getAsTeamType(String columnName) {
        try { return TeamType.valueOf(stringValue(super.get(columnName))); }
        catch (Exception e) { return null; }
    }

    public LeagueShard getAsLeagueShard(String columnName) {
        try { return LeagueShard.valueOf(stringValue(super.get(columnName))); }
        catch (Exception e) { return null; }
    }

    public TierDivisionType getAsTier(String columnName) {
        try { return TierDivisionType.valueOf(stringValue(super.get(columnName))); }
        catch (Exception e) { return null; }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value instanceof String string ? string : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertValue(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return type.cast(value);
        if (type == String.class) return type.cast(stringValue(value));
        if (type == Integer.class) return type.cast(value instanceof Number number ? number.intValue() : Integer.valueOf(stringValue(value)));
        if (type == Long.class) return type.cast(value instanceof Number number ? number.longValue() : Long.valueOf(stringValue(value)));
        if (type == Double.class) return type.cast(value instanceof Number number ? number.doubleValue() : Double.valueOf(stringValue(value)));
        if (type == Boolean.class) return type.cast(value instanceof Boolean booleanValue ? booleanValue : Boolean.valueOf(stringValue(value)));
        return (T) value;
    }
}
