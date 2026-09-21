package com.safjnest.redis;

public final class RedisMemoryParser {

    private RedisMemoryParser() {}

    public static Long parseUsedMemory(String info) {
        if (info == null || info.isBlank()) return null;
        for (String line : info.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("used_memory:") && !trimmed.startsWith("used_memory_")) {
                try {
                    return Long.parseLong(trimmed.substring("used_memory:".length()).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
