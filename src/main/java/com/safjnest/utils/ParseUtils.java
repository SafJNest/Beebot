package com.safjnest.utils;

import java.util.Locale;

public final class ParseUtils {

    private ParseUtils() {
    }

    public static int parseInt(String value) {
        return parseInt(value, 0);
    }

    public static int parseInt(String value, int defaultValue) {
        if (value == null)
            return defaultValue;

        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return defaultValue;

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static int parseInt(Object value) {
        if (value instanceof Number number)
            return number.intValue();

        if (value instanceof String string)
            return parseInt(string);

        return 0;
    }

    public static Integer parseIntOrNull(String value) {
        return parseIntExactOrNull(value == null ? null : value.trim());
    }

    public static Integer parseIntExactOrNull(String value) {
        if (value == null)
            return null;

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static long parseLong(String value) {
        if (value == null)
            return 0L;

        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return 0L;

        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public static Double parseDoubleOrNull(String value) {
        if (value == null)
            return null;

        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return null;

        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static <T extends Enum<T>> T valueOf(String value, Class<T> type) {
        if (value == null || type == null)
            return null;

        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return null;

        try {
            return Enum.valueOf(type, trimmed);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Enum.valueOf(type, trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static <T extends Enum<T>> T fromOrdinal(int ordinal, Class<T> type) {
        if (type == null)
            return null;

        T[] constants = type.getEnumConstants();
        if (constants == null || ordinal < 0 || ordinal >= constants.length)
            return null;

        return constants[ordinal];
    }

    public static <T extends Enum<T>> T fromOrdinal(String value, Class<T> type) {
        Integer ordinal = parseIntOrNull(value);

        return ordinal == null ? null : fromOrdinal(ordinal, type);
    }
}
