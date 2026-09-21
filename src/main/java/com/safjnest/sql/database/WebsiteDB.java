package com.safjnest.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import com.safjnest.sql.AbstractDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.spotify.SpotifyTokenManager;

public class WebsiteDB extends AbstractDB {

    private static WebsiteDB instance;
    static {
        instance = new WebsiteDB();
    }

    @Override
	protected String getDatabase() {
        return SettingsLoader.getSettings().getJsonSettings().getTestWebsiteDatabase().getDatabaseName();
	}

    public static WebsiteDB get() {
        return instance;
    }

    public static String getSpotifyUserToken(String userId) {
        QueryRecord res = instance.lineQuery("SELECT * FROM SpotifyToken WHERE discordId = '" + userId + "';");

        if (res == null || res.emptyValues()) 
            return null;
            
        if (res.get("expiresAt") != null && res.getAsLocalDateTime(res.get("expiresAt")).isBefore(java.time.LocalDateTime.now())) {
            System.out.println("Token expired, refreshing...");
            return SpotifyTokenManager.refreshUserToken(res.get("discordId"), res.get("refreshToken"));
        }

        return res.get("accessToken");
    }

    public static boolean updateSpotifyUserToken(String userId, String accessToken, String refreshToken, java.time.LocalDateTime expiresAt) {
        Connection c = instance.getConnection();
        String sql = "UPDATE SpotifyToken SET accessToken = ?, refreshToken = ?, expiresAt = ? WHERE discordId = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, accessToken);
            stmt.setString(2, refreshToken);
            stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            stmt.setString(4, userId);
            int rowsUpdated = stmt.executeUpdate();

            if(rowsUpdated == 0) {
                throw new SQLException("No rows updated, userId may not exist: " + userId);
            }

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
            return false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static boolean deleteSpotifyRefreshToken(String userId) {
        return instance.defaultQuery("DELETE FROM SpotifyToken WHERE discordId = '" + userId + "';");
    }
}
