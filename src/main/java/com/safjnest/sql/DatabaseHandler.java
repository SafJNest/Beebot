package com.safjnest.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.util.SettingsLoader;

/**
 * @deprecated This class is deprecated and will be removed.
 * Use Spring Boot's DataSource configuration instead.
 * 
 * Legacy database handler for fallback purposes only.
 */
@Deprecated
public class DatabaseHandler {
    private static HikariDataSource dataSource; // shared pool

    static {
        // Only initialize if Spring is not available (fallback mode)
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            
            DatabaseSettings settings = SettingsLoader.getSettings().getJsonSettings().getDatabase();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mariadb://" + settings.getHost() + "/?autoReconnect=true");
            config.setUsername(settings.getUsername());
            config.setPassword(settings.getPassword());
            config.setAutoCommit(false);

            config.setMaximumPoolSize(117);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Deprecated
    protected static Connection getConnection(String db) throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseHandler is deprecated. Use Spring DataSource instead!");
        }
        Connection conn = dataSource.getConnection();

        // Switch to subclass database
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + db);
        }
        return conn;
    }

}
