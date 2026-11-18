package com.safjnest.lol.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.safjnest.utils.ParseUtils;

public final class FilterCodec {

    private FilterCodec() {
    }

    public static String encode(String raw) {
        if (raw == null)
            return "";

        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodeUrl(String raw) {
        if (raw == null)
            return "";

        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String key) {
        if (key == null)
            return "";

        return new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8);
    }

    public static String decodeUrl(String key) {
        if (key == null)
            return "";

        return new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
    }

    public static String join(String... parts) {
        if (parts == null || parts.length == 0)
            return "";

        return String.join("|", parts);
    }

    public static String[] split(String raw) {
        if (raw == null)
            return new String[0];

        return raw.split("\\|", -1);
    }

    public static String encodeValue(Object value) {
        return value != null ? value.toString() : "*";
    }

    public static String encodeInt(int value) {
        return value != 0 ? String.valueOf(value) : "*";
    }

    public static String encodeLong(long value) {
        return value != 0 ? String.valueOf(value) : "*";
    }

    public static String encodeEnum(Enum<?> value) {
        return value != null ? value.name() : "*";
    }

    public static String encodeOrdinal(Enum<?> value) {
        return value != null ? String.valueOf(value.ordinal()) : "*";
    }

    public static String decodeString(String part) {
        if (part == null)
            return null;

        String trimmed = part.trim();

        return "*".equals(trimmed) ? null : trimmed;
    }

    public static int decodeInt(String part) {
        return ParseUtils.parseInt(decodeString(part));
    }

    public static long decodeLong(String part) {
        return ParseUtils.parseLong(decodeString(part));
    }

    public static <T extends Enum<T>> T decodeEnum(String part, Class<T> type) {
        if (part == null)
            return null;

        String trimmed = part.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed))
            return null;

        return ParseUtils.valueOf(trimmed, type);
    }

    public static <T extends Enum<T>> T decodeOrdinal(String part, Class<T> type) {
        return ParseUtils.fromOrdinal(decodeString(part), type);
    }
}
