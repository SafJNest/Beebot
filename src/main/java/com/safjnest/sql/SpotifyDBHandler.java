package com.safjnest.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyArtist;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.spotify.type.SpotifyMessageType;

public class SpotifyDBHandler extends AbstractDB {

    private static BotDB instance;
    static {
        instance = new BotDB();
    }

    @Override
	protected String getDatabase() {
        return SettingsLoader.getSettings().getJsonSettings().getSpotifyDatabase().getDatabaseName();
	}

    public static BotDB get() {
        return instance;
    }

    public static void insertBatch(List<SpotifyTrackStreaming> trackList, String userId) throws SQLException, NoSuchAlgorithmException {
        Connection conn = instance.getConnection();
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

        try (Connection conn = instance.getConnection();
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

        try (Connection conn = instance.getConnection();
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

        try (Connection conn = instance.getConnection();
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