package com.safjnest.lol.utils;

import java.util.Locale;

public final class NumberUtils {

    private NumberUtils() {}

    public static int parseInt(String value) {
        return parseInt(value, 0);
    }

    public static int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string) return parseInt(string, 0);
        return 0;
    }

    public static long parseLong(String value) {
        return parseLong(value, 0L);
    }

    public static long parseLong(String value, long defaultValue) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static long parseLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) return parseLong(string, 0L);
        return 0L;
    }

    public static double parseDouble(String value) {
        return parseDouble(value, 0d);
    }

    public static double parseDouble(String value, double defaultValue) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static double parseDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) return parseDouble(string, 0d);
        return 0d;
    }

    public static Integer parseInteger(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Long parseLongObject(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Double parseDoubleObject(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int parseIntStar(String value) {
        if (value == null) return 0;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return 0;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static long parseLongStar(String value) {
        if (value == null) return 0L;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return 0L;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public static <T extends Enum<T>> T valueOf(String value, Class<T> type) {
        return valueOf(value, type, null);
    }

    public static <T extends Enum<T>> T valueOf(String value, Class<T> type, T defaultValue) {
        if (value == null || type == null) return defaultValue;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return defaultValue;
        try {
            return Enum.valueOf(type, trimmed);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Enum.valueOf(type, trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    public static <T extends Enum<T>> T fromOrdinal(int ordinal, Class<T> type) {
        if (type == null) return null;
        T[] constants = type.getEnumConstants();
        if (constants == null || ordinal < 0 || ordinal >= constants.length) return null;
        return constants[ordinal];
    }

    public static <T extends Enum<T>> T fromOrdinal(String value, Class<T> type) {
        if (value == null || type == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return null;
        try {
            int ordinal = Integer.parseInt(trimmed);
            return fromOrdinal(ordinal, type);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static <T extends Enum<T>> T fromOrdinal(String value, Class<T> type, T defaultValue) {
        T result = fromOrdinal(value, type);
        return result != null ? result : defaultValue;
    }
}
