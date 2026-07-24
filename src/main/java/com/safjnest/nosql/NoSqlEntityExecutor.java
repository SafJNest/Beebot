package com.safjnest.nosql;

import java.util.List;
import java.util.Map;

final class NoSqlEntityExecutor {

    @FunctionalInterface
    interface EntityWriter {
        boolean execute(String collection, String id, List<Map<String, Object>> operations, Map<String, Object> filters, boolean upsert);
    }

    private static final EntityWriter DEFAULT_WRITER = MongoDB::applyEntityUpdate;
    private static volatile EntityWriter writer = DEFAULT_WRITER;

    private NoSqlEntityExecutor() {
    }

    static boolean execute(
            String collection,
            String id,
            List<Map<String, Object>> operations,
            Map<String, Object> filters,
            boolean upsert) {
        return writer.execute(collection, id, operations, filters, upsert);
    }

    static void installWriterForTests(EntityWriter testWriter) {
        writer = testWriter == null ? DEFAULT_WRITER : testWriter;
    }

    static void resetWriter() {
        writer = DEFAULT_WRITER;
    }
}
