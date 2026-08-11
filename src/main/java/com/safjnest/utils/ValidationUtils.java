package com.safjnest.utils;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static boolean valid(Object... values) {
        if (values == null || values.length == 0) return false;

        for (Object value : values) {
            if (value == null || value instanceof String text && text.isBlank()) return false;
        }
        return true;
    }
}
