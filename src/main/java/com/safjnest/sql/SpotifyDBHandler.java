package com.safjnest.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyArtist;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.spotify.SpotifyMessageType;

public class SpotifyDBHandler {
    private static String hostName;
    private static String database;
    private static String user;
    private static String password;

    private static HikariDataSource dataSource;

    private static HashMap<Long, List<String>> queryAnalytics = new HashMap<>();

    static {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    static {
        DatabaseSettings settings = SettingsLoader.getSettings().getConfig().isTesting() 
            ? SettingsLoader.getSettings().getJsonSettings().getSpotifyDatabase() 
            :  SettingsLoader.getSettings().getJsonSettings().getSpotifyDatabase(); //da cambiare con il database di produzione

        hostName = settings.getHost();
        database = settings.getDatabaseName();
        user = settings.getUsername();
        password = settings.getPassword();

        connectIfNot();
    }

    private static void connectIfNot() {
        if (dataSource != null) return;

        initializeConnectionPool();
        if(!dataSource.isRunning())
            BotLogger.error("[SQL] Connection to the extreme db failed!");

        BotLogger.info("[SQL] Connection to the extreme db successful!");
    }

    public static void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mariadb://" + hostName + "/" + database + "?autoReconnect=true");
        config.setUsername(user);
        config.setPassword(password);
        config.setAutoCommit(false);

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() {
        connectIfNot();
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void insertAnalytics(String query) {
        List<String> queries = queryAnalytics.getOrDefault(System.currentTimeMillis(), new ArrayList<>());
        queries.add(query);
        queryAnalytics.put(System.currentTimeMillis(), queries);
    }

    public static QueryCollection safJQuery(String query) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return null;

        QueryCollection result = new QueryCollection();

        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                QueryRecord beeRow = new QueryRecord(rs);
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
                result.add(beeRow);
            }
            insertAnalytics(query);
            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        return result;
    }


    /**
     * Method used for returning a {@link com.safjnest.sql.QueryCollection result} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryCollection safJQuery(Statement stmt, String query) throws SQLException {
        connectIfNot();

        QueryCollection result = new QueryCollection();

        ResultSet rs = stmt.executeQuery(query);
        ResultSetMetaData rsmd = rs.getMetaData();

        while (rs.next()) {
            QueryRecord beeRow = new QueryRecord(rs);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
            result.add(beeRow);
        }
        insertAnalytics(query);

        return result;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryRecord fetchJRow(String query) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return null;
        QueryRecord beeRow = new QueryRecord(null);
        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            beeRow.setResultSet(rs);
            ResultSetMetaData rsmd = rs.getMetaData();
            if (rs.next()) {
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnName(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
            }
            insertAnalytics(query);
            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        return beeRow;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query.
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryRecord fetchJRow(Statement stmt, String query) throws SQLException {
        connectIfNot();


        ResultSet rs = stmt.executeQuery(query);
        QueryRecord beeRow = new QueryRecord(rs);

        ResultSetMetaData rsmd = rs.getMetaData();

        if (rs.next()) {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
        }
        insertAnalytics(query);
        return beeRow;
    }


    /**
     * Run one or more queries using the default statement
     * @param queries
     */
    public static boolean runQuery(String... queries) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            for (String query : queries)
                stmt.execute(query);
            insertAnalytics(queries.toString());
            c.commit();
            return true;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return false;
    }

    public static CompletableFuture<Void> runQueryAsync(String... queries) {
        return new ChronoTask() {
            @Override
            public void run() {
                runQuery(queries);
            }
        }.queueFuture();
    }


    /**
     * Run one or more queries with a specific statement
     * <p>
     * Only for insert, update and delete
     * @param stmt
     * @param queries
     * @throws SQLException
     */
    public static void runQuery(Statement stmt, String... queries) throws SQLException {
        connectIfNot();

        for (String query : queries)
            stmt.execute(query);

        insertAnalytics(queries.toString());

    }

    public static HashMap<Long, List<String>> getQueryAnalytics() {
        return queryAnalytics;
    }

    public static void insertBatch(List<SpotifyTrackStreaming> trackList, String userId) throws SQLException, NoSuchAlgorithmException {
        Connection conn = getConnection();
        conn.setAutoCommit(false);

        PreparedStatement insertArtist = conn.prepareStatement(
            "INSERT IGNORE INTO artists (artist_id, name) VALUES (?, ?)"
        );

        PreparedStatement insertAlbum = conn.prepareStatement(
            "INSERT IGNORE INTO albums (album_id, title, artist_id) VALUES (?, ?, ?)"
        );

        PreparedStatement insertTrack = conn.prepareStatement(
            "INSERT IGNORE INTO tracks (track_id, title, album_id) VALUES (?, ?, ?)"
        );

        PreparedStatement insertUser = conn.prepareStatement(
            "INSERT IGNORE INTO users (discord_user_id) VALUES (?)"
        );

        PreparedStatement insertStreaming = conn.prepareStatement(
            "INSERT IGNORE INTO track_streamings (user_id, track_id, streamed_at, duration_ms) VALUES (?, ?, ?, ?)"
        );

        Set<String> seenArtists = new HashSet<>();
        Set<String> seenAlbums = new HashSet<>();
        Set<String> seenTracks = new HashSet<>();

        insertUser.setString(1, userId);
        insertUser.addBatch();

        for (SpotifyTrackStreaming t : trackList) {
            String artistId = generateId(t.getArtistName());
            String albumId = generateId(t.getAlbumName() + artistId);
            String trackId = t.getSpotifyTrackUri();

            if (seenArtists.add(artistId)) {
                insertArtist.setString(1, artistId);
                insertArtist.setString(2, t.getArtistName());
                insertArtist.addBatch();
            }

            if (seenAlbums.add(albumId)) {
                insertAlbum.setString(1, albumId);
                insertAlbum.setString(2, t.getAlbumName());
                insertAlbum.setString(3, artistId);
                insertAlbum.addBatch();
            }

            if (seenTracks.add(trackId)) {
                insertTrack.setString(1, trackId);
                insertTrack.setString(2, t.getTrack().getName());
                insertTrack.setString(3, albumId);
                insertTrack.addBatch();
            }

            insertStreaming.setString(1, userId);
            insertStreaming.setString(2, trackId);
            insertStreaming.setTimestamp(3, Timestamp.from(Instant.parse(t.getTs())));
            insertStreaming.setLong(4, t.getMsPlayed());
            insertStreaming.addBatch();
        }

        System.err.println("Inserted " + trackList.size() + " tracks into the database.");

        insertArtist.executeBatch();
        insertAlbum.executeBatch();
        insertTrack.executeBatch();
        insertUser.executeBatch();
        insertStreaming.executeBatch();

        conn.commit();

        insertArtist.close();
        insertAlbum.close();
        insertTrack.close();
        insertUser.close();
        insertStreaming.close();
    }

    public static String generateId(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 22);
    }

    public static List<?> getTopItems(SpotifyMessageType type, String userId, int limit, int offset) {
        connectIfNot();

        switch (type) {
            case TRACKS:
                return getTopTracks(userId, limit, offset);
            case ALBUMS:
                return getTopAlbums(userId, limit, offset);
            case ARTISTS:
                return getTopArtists(userId, limit, offset);
            default:
                throw new IllegalArgumentException("Unsupported SpotifyMessageType: " + type);
        }
    }

    public static List<SpotifyTrack> getTopTracks(String userId, int limit, int index) {
        connectIfNot();

        String query = """
            SELECT 
                t.track_id,
                t.title AS track_title,
                al.title AS album_title,
                GROUP_CONCAT(DISTINCT ar.name ORDER BY ar.name SEPARATOR ', ') AS artist_names,
                COUNT(ts.streaming_id) AS play_count
            FROM track_streamings ts
            JOIN users u ON u.discord_user_id = ts.user_id
            JOIN tracks t ON t.track_id = ts.track_id
            LEFT JOIN albums al ON al.album_id = t.album_id
            LEFT JOIN artists ar ON ar.artist_id = al.artist_id
            WHERE u.discord_user_id = ?
            GROUP BY t.track_id
            ORDER BY play_count DESC
            LIMIT ? OFFSET ?
        """;

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            stmt.setInt(2, limit);
            stmt.setInt(3, index);
            ResultSet rs = stmt.executeQuery();

            List<SpotifyTrack> tracks = new ArrayList<>();
            while (rs.next()) {
                SpotifyTrack track = new SpotifyTrack(
                    rs.getString("track_title"),
                    rs.getString("artist_names"),
                    rs.getString("album_title"),
                    rs.getString("track_id"),
                    rs.getInt("play_count"),
                    null
                );
                tracks.add(track);
            }
            return tracks;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static List<SpotifyAlbum> getTopAlbums(String userId, int limit, int index) {
        connectIfNot();

        String query = """
            SELECT
                a.album_id,
                a.title AS album_title,
                ar.name AS artist_name,
                COUNT(ts.streaming_id) AS play_count,
                (
                    SELECT t_sub.track_id
                    FROM tracks t_sub
                    WHERE t_sub.album_id = a.album_id
                    LIMIT 1
                ) AS sample_track_uri
                FROM albums a
                JOIN artists ar ON a.artist_id = ar.artist_id
                JOIN tracks t ON t.album_id = a.album_id
                JOIN track_streamings ts ON ts.track_id = t.track_id
                WHERE ts.user_id = ?
                GROUP BY a.album_id, a.title, ar.name
                ORDER BY play_count DESC
                LIMIT ? OFFSET ?;
            """;

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            stmt.setInt(2, limit);
            stmt.setInt(3, index);
            ResultSet rs = stmt.executeQuery();

            List<SpotifyAlbum> albums = new ArrayList<>();
            while (rs.next()) {
                SpotifyAlbum album = new SpotifyAlbum(
                    rs.getString("album_title"),
                    rs.getString("artist_name"),
                    new ArrayList<SpotifyTrack>() {{
                        add(new SpotifyTrack("UNKNOWN", rs.getString("artist_name"), rs.getString("album_title"), rs.getString("sample_track_uri")));
                    }},
                    rs.getInt("play_count")
                );
                albums.add(album);
            }
            return albums;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static List<SpotifyArtist> getTopArtists(String userId, int limit, int index) {
        connectIfNot();

        String query = """
            SELECT
            ar.artist_id,
            ar.name AS artist_name,
            COUNT(ts.streaming_id) AS play_count,
            (
                SELECT t_sub.track_id
                FROM tracks t_sub
                JOIN albums a_sub ON t_sub.album_id = a_sub.album_id
                WHERE a_sub.artist_id = ar.artist_id
                LIMIT 1
            ) AS sample_track_uri
            FROM track_streamings ts
            JOIN tracks t ON ts.track_id = t.track_id
            JOIN albums a ON t.album_id = a.album_id
            JOIN artists ar ON a.artist_id = ar.artist_id
            WHERE ts.user_id = ?
            GROUP BY ar.artist_id, ar.name
            ORDER BY play_count DESC
            LIMIT ? OFFSET ?;
            """;

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            stmt.setInt(2, limit);
            stmt.setInt(3, index);
            ResultSet rs = stmt.executeQuery();

            List<SpotifyArtist> albums = new ArrayList<>();
            while (rs.next()) {
                SpotifyArtist album = new SpotifyArtist(
                    rs.getString("artist_name"),
                    rs.getInt("play_count"),
                    rs.getString("sample_track_uri")
                );
                albums.add(album);
            }
            return albums;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        
    }

}