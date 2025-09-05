package com.safjnest.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.util.SettingsLoader;


// TheAllHandler che gestisce connessioni e quuery a livello query e cose varie

// abstract class DBHandler che ha il nome del database a cui si deve connettere

// N classi che estendono DBHandler e implementano i metodi per le query


public class DatabaseHandler {
    private static HikariDataSource dataSource; // shared pool

    static {

        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

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
    }

    protected static Connection getConnection(String db) throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Call initialize() first!");
        }
        Connection conn = dataSource.getConnection();

        // Switch to subclass database
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + db);
        }
        return conn;
    }

}
