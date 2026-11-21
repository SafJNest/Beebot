package com.safjnest.bot.audio;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundboardData;
import com.safjnest.database.sql.database.BotDB.SoundboardAccess;
import com.safjnest.exception.Exception;

public class SoundboardThumbnailsTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};

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
                    thumbnail VARCHAR(128), public BOOLEAN DEFAULT FALSE, sort_mode VARCHAR(12)
                )
                """);
            statement.execute("CREATE TABLE guild (guild_id VARCHAR(19) PRIMARY KEY)");
            statement.execute("INSERT INTO guild VALUES ('1')");
            statement.execute("CREATE TABLE sound (id INT PRIMARY KEY, guild_id VARCHAR(19), user_id VARCHAR(19), public BOOLEAN)");
            statement.execute("INSERT INTO sound VALUES (1,'1','2',TRUE)");
            statement.execute("""
                CREATE TABLE soundboard_sounds (id INT REFERENCES soundboard(id), sound_id INT REFERENCES sound(id), position INT NOT NULL)
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
    public void creationUploadsOnlyAfterSqlAndRollsBackIfUploadFails() throws SQLException {
        assertThrows(Exception.class, () -> database.insertSoundBoard("Bad sound", "1", "2",
            id -> { throw new AssertionError("Upload called before validating sound relations"); }, "9"));

        IllegalStateException failed = new IllegalStateException("Injected upload failure");
        assertSame(failed, assertThrows(IllegalStateException.class,
            () -> database.insertSoundBoard("Failed upload", "1", "2", id -> { throw failed; }, "1")));
        assertEquals(0, countRows("soundboard"));
        assertEquals(0, countRows("soundboard_sounds"));

        SoundboardThumbnails thumbnails = new SoundboardThumbnails((key, image) -> {
            assertArrayEquals(PNG, image.bytes());
            assertEquals("image/png", image.contentType());
        });
        SoundboardThumbnails.Image cover = new SoundboardThumbnails.Image(PNG, "png", "image/png");
        String id = database.insertSoundBoard("New cover", "1", "2", value -> thumbnails.upload(value, cover), "1");
        SoundboardData saved = database.getSoundboardByID(id);
        assertKey(saved.thumbnailKey(), Integer.parseInt(id), "png");
        assertEquals(1, countRows("soundboard_sounds"));

        String replacement = thumbnails.upload(id, cover);
        assertNotEquals(saved.thumbnailKey(), replacement);
        assertThrows(Exception.class, () -> database.replaceSoundboardThumbnail(id, new SoundboardAccess("3", "9", true), ignored -> replacement));
        assertEquals(saved, database.getSoundboardByID(id));
        assertEquals(replacement, database.replaceSoundboardThumbnail(id, new SoundboardAccess("1", "9", true), ignored -> replacement));
        assertEquals(new SoundboardData("New cover", replacement), database.getSoundboardByID(id));
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

    private int countRows(String table) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();

            return rows.getInt(1);
        }
    }
}
