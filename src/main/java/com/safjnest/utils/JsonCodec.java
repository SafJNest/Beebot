package com.safjnest.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;

/** Shared JSON codec for SQL text and structured Mongo BSON payloads. */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true)
        .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .build();

    private JsonCodec() {}

    public static String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize " + value.getClass().getName(), exception);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    public static Document toDocument(Object value) {
        if (value == null) return null;
        try {
            return Document.parse(toJson(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to convert " + value.getClass().getName() + " to BSON", exception);
        }
    }

    public static <T> T fromDocument(Object value, Class<T> type) {
        if (value == null) return null;
        return fromJson(toJson(value), type);
    }
}
