package com.safjnest.mongo.codec;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

final class MongoDocumentValues {

    private MongoDocumentValues() {}

    static String string(Document document, String key) {
        return document.getString(key);
    }

    static int integer(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value != null ? value.intValue() : 0;
    }

    static long longValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    static boolean bool(Document document, String key) {
        Boolean value = document.getBoolean(key);
        return value != null && value;
    }

    static <E extends Enum<E>> E enumValue(Document document, String key, Class<E> enumType) {
        String value = document.getString(key);
        if (value == null) return null;

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static List<Integer> integerList(Document document, String key) {
        List<?> values = document.getList(key, Object.class);
        if (values == null || values.isEmpty()) return List.of();

        List<Integer> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof Number number) result.add(number.intValue());
        }
        return result;
    }
}
