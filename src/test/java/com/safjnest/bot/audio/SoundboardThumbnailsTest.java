package com.safjnest.bot.audio;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.safjnest.bot.audio.SoundboardThumbnails.Migration;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundboardData;
import com.safjnest.exception.Exception;

public class SoundboardThumbnailsTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};

    private String url;
    private BotDB database;

    @Before
    public void setup() throws SQLException {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        database = new BotDB(this::open);

        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE soundboard (
                    id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), guild_id VARCHAR(19), user_id VARCHAR(19),
                    thumbnail BLOB, thumbnail_key VARCHAR(128)
                )
                """);
            statement.execute("CREATE TABLE sound (id INT PRIMARY KEY)");
            statement.execute("INSERT INTO sound VALUES (1)");
            statement.execute("""
                CREATE TABLE soundboard_sounds (id INT REFERENCES soundboard(id), sound_id INT REFERENCES sound(id))
                """);
        }
    }

    @After
    public void cleanup() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    public void migrationKeepsLegacyBytesAndRetriesOnlyFailedRows() throws SQLException {
        seedThumbnail(1, PNG);
        seedThumbnail(2, JPEG);
        seedThumbnail(3, null);
        List<String> uploads = new ArrayList<>();
        SoundboardThumbnails first = new SoundboardThumbnails(database, (key, image) -> {
            uploads.add(key);
            if (image.contentType().equals("image/jpeg"))
                throw new IllegalStateException("Injected upload failure");

            assertEquals("image/png", image.contentType());
            assertArrayEquals(PNG, image.bytes());
        });

        assertArrayEquals(PNG, database.getSoundboardByID("1").legacyThumbnail());
        assertEquals(2, database.countPendingSoundboardThumbnails());
        assertEquals(new Migration(1, 0, List.of(2), 1), first.migrate());
        assertEquals(2, uploads.size());
        assertKey(uploads.get(0), 1, "png");
        assertKey(uploads.get(1), 2, "jpg");
        assertEquals(uploads.get(0), database.getSoundboardByID("1").thumbnailKey());
        assertNull(database.getSoundboardByID("1").legacyThumbnail());
        assertArrayEquals(PNG, storedBlob(1));
        assertArrayEquals(JPEG, database.getSoundboardByID("2").legacyThumbnail());

        SoundboardThumbnails retry = new SoundboardThumbnails(database, (key, image) -> {
            uploads.add(key);
            assertEquals("image/jpeg", image.contentType());
        });
        assertEquals(new Migration(1, 0, List.of(), 0), retry.migrate());
        assertEquals(3, uploads.size());
        assertNotEquals(uploads.get(1), uploads.get(2));
        assertEquals(uploads.get(2), database.getSoundboardByID("2").thumbnailKey());
        assertEquals(new Migration(0, 0, List.of(), 0), retry.migrate());
        assertEquals(3, uploads.size());

        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE soundboard DROP COLUMN thumbnail");
        }
        assertEquals(0, database.countPendingSoundboardThumbnails());
        assertEquals(uploads.get(0), database.getSoundboardByID("1").thumbnailKey());
        assertEquals(new SoundboardData("Board 3", null, null), database.getSoundboardByID("3"));

        String uncovered = database.insertSoundBoard("No cover", "1", "2", null, "1");
        assertEquals(new SoundboardData("No cover", null, null), database.getSoundboardByID(uncovered));
    }

    @Test
    public void migrationCannotOverwriteAReplacement() throws SQLException {
        seedThumbnail(1, PNG);
        SoundboardThumbnails thumbnails = new SoundboardThumbnails(database, (key, image) -> {
            assertFalse(database.updateSoundboardThumbnail("1", "3", "9", "unauthorized"));
            assertTrue(database.updateSoundboardThumbnail("1", "1", "9", "new-cover"));
        });

        assertEquals(new Migration(0, 1, List.of(), 0), thumbnails.migrate());
        assertEquals("new-cover", database.getSoundboardByID("1").thumbnailKey());
        assertArrayEquals(PNG, storedBlob(1));
    }

    @Test
    public void unrecognizedLegacyImageIsReportedAndRetained() throws SQLException {
        byte[] unknown = {1, 2, 3, 4};
        seedThumbnail(1, unknown);
        SoundboardThumbnails thumbnails = new SoundboardThumbnails(database, (key, image) -> fail("Invalid image uploaded"));

        assertEquals(new Migration(0, 0, List.of(1), 1), thumbnails.migrate());
        assertNull(database.getSoundboardByID("1").thumbnailKey());
        assertArrayEquals(unknown, storedBlob(1));
    }

    @Test
    public void creationUploadsOnlyAfterSqlAndRollsBackIfUploadFails() throws SQLException {
        assertThrows(Exception.class, () -> database.insertSoundBoard("Bad sound", "1", "2",
            id -> { throw new AssertionError("Upload called before validating sound relations"); }, "9"));

        IllegalStateException failed = new IllegalStateException("Injected upload failure");
        assertSame(failed, assertThrows(IllegalStateException.class,
            () -> database.insertSoundBoard("Failed upload", "1", "2", id -> { throw failed; }, "1")));
        assertEquals(0, countRows("soundboard"));
        assertEquals(0, countRows("soundboard_sounds"));

        String id = database.insertSoundBoard("New cover", "1", "2", value -> "cover-for-" + value, "1");
        assertEquals("cover-for-" + id, database.getSoundboardByID(id).thumbnailKey());
        assertNull(storedBlob(Integer.parseInt(id)));
        assertEquals(1, countRows("soundboard_sounds"));
    }

    /* -------------------------------------------------------------------------- */

    private void assertKey(String key, int id, String extension) {
        String prefix = "soundboard/thumbnail/" + id + "/";
        String suffix = "." + extension;
        assertTrue(key.startsWith(prefix));
        assertTrue(key.endsWith(suffix));
        assertNotNull(UUID.fromString(key.substring(prefix.length(), key.length() - suffix.length())));
    }

    private Connection open() {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void seedThumbnail(int id, byte[] bytes) throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO soundboard (id, name, guild_id, user_id, thumbnail) VALUES (?, ?, '1', '2', ?)")) {
            statement.setInt(1, id);
            statement.setString(2, "Board " + id);
            statement.setBytes(3, bytes);
            statement.executeUpdate();
        }
    }

    private int countRows(String table) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();

            return rows.getInt(1);
        }
    }

    private byte[] storedBlob(int id) throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT thumbnail FROM soundboard WHERE id = ?")) {
            statement.setInt(1, id);

            try (ResultSet rows = statement.executeQuery()) {
                rows.next();

                return rows.getBytes(1);
            }
        }
    }
}
