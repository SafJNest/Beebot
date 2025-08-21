package com.safjnest.sql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.dv8tion.jda.api.entities.Message.Attachment;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;


// TheAllHandler che gestisce connessioni e quuery a livello safjquery e cose varie

// abstract class DBHandler che ha il nome del database a cui si deve connettere

// N classi che estendono DBHandler e implementano i metodi per le query


public class Databaseplusmathpowhandler2 {
    private static HikariDataSource dataSource; // shared pool

    static {

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

    private static void connectIfNot() {
        if (dataSource != null) return;
        if(!dataSource.isRunning())
            BotLogger.error("[SQL] Connection to the extreme db failed!");

        BotLogger.info("[SQL] Connection to the extreme db successful!");
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
