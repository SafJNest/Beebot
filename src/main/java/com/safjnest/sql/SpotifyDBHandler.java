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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;

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
        Connection conn = getConnection(); // tuo metodo per ottenere connessione
        conn.setAutoCommit(false);

        // PreparedStatements per batch
        PreparedStatement insertArtist = conn.prepareStatement(
            "INSERT IGNORE INTO artists (artist_id, name) VALUES (?, ?)"
        );

        PreparedStatement insertAlbum = conn.prepareStatement(
            "INSERT IGNORE INTO albums (album_id, title) VALUES (?, ?)"
        );

        PreparedStatement insertAlbumArtist = conn.prepareStatement(
            "INSERT IGNORE INTO album_artists (album_id, artist_id) VALUES (?, ?)"
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
        Set<String> seenAlbumArtists = new HashSet<>();
        Set<String> seenTracks = new HashSet<>();

        for (SpotifyTrackStreaming t : trackList) {
            // Genera gli ID deterministici
            String artistId = generateId(t.getArtistName());
            String albumId = generateId(t.getAlbumName() + artistId);
            String trackId = t.getSpotifyTrackUri();

            // Insert artist
            if (seenArtists.add(artistId)) {
                insertArtist.setString(1, artistId);
                insertArtist.setString(2, t.getArtistName());
                insertArtist.addBatch();
            }

            // Insert album
            if (seenAlbums.add(albumId)) {
                insertAlbum.setString(1, albumId);
                insertAlbum.setString(2, t.getAlbumName());
                insertAlbum.addBatch();
            }


            String albumArtistKey = albumId + "|" + artistId;
            if (seenAlbumArtists.add(albumArtistKey)) {
                insertAlbumArtist.setString(1, albumId);
                insertAlbumArtist.setString(2, artistId);
                insertAlbumArtist.addBatch();
            }

            if (seenTracks.add(trackId)) {
                insertTrack.setString(1, trackId);
                insertTrack.setString(2, t.getTrack().getName());
                insertTrack.setString(3, albumId);
                insertTrack.addBatch();
            }

            insertUser.setString(1, userId);
            insertUser.addBatch();


            insertStreaming.setString(1, userId);
            insertStreaming.setString(2, trackId);
            insertStreaming.setTimestamp(3, Timestamp.from(Instant.parse(t.getTs())));
            insertStreaming.setInt(4, (int) 0);
            insertStreaming.addBatch();
        }

        // Esecuzione batch
        insertArtist.executeBatch();
        insertAlbum.executeBatch();
        insertAlbumArtist.executeBatch();
        insertTrack.executeBatch();
        insertUser.executeBatch();
        insertStreaming.executeBatch();

        conn.commit();

        // Close statements
        insertArtist.close();
        insertAlbum.close();
        insertAlbumArtist.close();
        insertTrack.close();
        insertUser.close();
        insertStreaming.close();
    }

  public static String generateId(String input) throws NoSuchAlgorithmException {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 22);
  }

}