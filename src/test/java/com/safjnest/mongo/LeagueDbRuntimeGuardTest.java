package com.safjnest.mongo;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class LeagueDbRuntimeGuardTest {

    @Test
    public void leagueDbIsReferencedOnlyByMigration() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("LeagueDB.java"))
                    .filter(path -> !path.endsWith("MongoMigration.java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("com.safjnest.sql.database.LeagueDB") || source.contains("LeagueDB.")) {
                                violations.add(path.toString());
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Unable to read source " + path, exception);
                        }
                    });
        }

        assertTrue("LeagueDB runtime references: " + violations, violations.isEmpty());
    }
}
