package com.safjnest.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.sql.QueryRecord;

public class GuildSettingsDB {

    private static final Map<String, String> GENERAL_COLUMNS = Map.of(
        "prefix", "prefix", "experienceEnabled", "exp_enabled", "voice", "name_tts",
        "language", "language_tts", "leagueShard", "league_shard");
    private static final Map<String, String> BLACKLIST_COLUMNS = Map.of(
        "enabled", "blacklist_enabled", "channelId", "blacklist_channel", "threshold", "threshold");
    private static final Map<String, String> CHANNEL_COLUMNS = Map.of(
        "experienceEnabled", "exp_enabled", "experienceModifier", "exp_modifier",
        "statisticsEnabled", "stats_enabled", "leagueShard", "league_shard");
    private static final Map<String, String> ALERT_COLUMNS = Map.of(
        "message", "message", "privateMessage", "private_message", "channelId", "channel",
        "enabled", "enabled", "sendType", "send_type");

    private final Supplier<Connection> connections;
    private final Supplier<String> defaultPrefix;

    public record Snapshot(QueryRecord guild, List<QueryRecord> channels, List<AlertData> alerts, List<QueryRecord> actions) {}

    public GuildSettingsDB() {
        this(() -> BotDB.get().getConnection(), com.safjnest.core.Bot::getPrefix);
    }

    public GuildSettingsDB(Supplier<Connection> connections) {
        this(connections, () -> "!");
    }

    private GuildSettingsDB(Supplier<Connection> connections, Supplier<String> defaultPrefix) {
        this.connections = connections;
        this.defaultPrefix = defaultPrefix;
    }

    public String getDefaultPrefix() {
        return defaultPrefix.get();
    }

    public Snapshot load(String guildId) {
        try (Connection connection = connect()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            Snapshot snapshot = read(connection, guildId);
            connection.commit();
            return snapshot;
        } catch (SQLException exception) {
            throw new IllegalStateException("Guild settings storage is unavailable", exception);
        }
    }

    public void saveSettings(String guildId, String block, String channelId, Map<String, Object> values) {
        try (Connection connection = connect()) {
            try {
                lockGuild(connection, guildId);
                if (block.equals("channels")) {
                    List<QueryRecord> rows = query(connection,
                        "SELECT id FROM channel WHERE guild_id = ? AND channel_id = ?", guildId, channelId);
                    if (rows.isEmpty())
                        execute(connection, "INSERT INTO channel (guild_id, channel_id) VALUES (?, ?)", guildId, channelId);
                    update(connection, "channel", CHANNEL_COLUMNS, values, "guild_id = ? AND channel_id = ?", guildId, channelId);
                } else {
                    Map<String, String> columns = block.equals("general") ? GENERAL_COLUMNS : BLACKLIST_COLUMNS;
                    update(connection, "guild", columns, values, "guild_id = ?", guildId);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save guild settings", exception);
        }
    }

    public AlertData saveAlert(String guildId, AlertType type, String key, Map<String, Object> values) {
        try (Connection connection = connect()) {
            try {
                lockGuild(connection, guildId);
                AlertData existing = find(readAlerts(connection, guildId), type, key);
                int id;
                if (existing == null) {
                    id = insert(connection,
                        "INSERT INTO alert (guild_id, message, private_message, channel, enabled, send_type, type) VALUES (?, '', '', NULL, 1, ?, ?)",
                        guildId, AlertSendType.CHANNEL.ordinal(), type.ordinal());
                    if (type == AlertType.TWITCH)
                        execute(connection, "INSERT INTO alert_twitch (alert_id, streamer_id, role_id) VALUES (?, ?, NULL)", id, key);
                    if (type == AlertType.REWARD)
                        execute(connection, "INSERT INTO alert_reward (alert_id, level, temporary) VALUES (?, ?, 0)", id, Integer.parseInt(key));
                } else id = existing.getID();

                update(connection, "alert", ALERT_COLUMNS, values, "id = ? AND guild_id = ?", id, guildId);
                if (values.containsKey("roleIds")) {
                    execute(connection, "DELETE FROM alert_role WHERE alert_id = ?", id);
                    for (Object roleId : (List<?>) values.get("roleIds"))
                        execute(connection, "INSERT INTO alert_role (alert_id, role_id) VALUES (?, ?)", id, roleId);
                }
                if (type == AlertType.TWITCH && values.containsKey("roleId"))
                    execute(connection, "UPDATE alert_twitch SET role_id = ? WHERE alert_id = ?", values.get("roleId"), id);
                if (type == AlertType.REWARD && values.containsKey("temporary"))
                    execute(connection, "UPDATE alert_reward SET temporary = ? WHERE alert_id = ?", values.get("temporary"), id);

                AlertData saved = find(readAlerts(connection, guildId), type, key);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save alert", exception);
        }
    }

    public static AlertData find(List<AlertData> alerts, AlertType type, String key) {
        for (AlertData alert : alerts) {
            if (alert.getType() != type) continue;
            if (type == AlertType.TWITCH && !((TwitchData) alert).getStreamer().equals(key)) continue;
            if (type == AlertType.REWARD && !String.valueOf(((RewardData) alert).getLevel()).equals(key)) continue;
            return alert;
        }
        return null;
    }

    // ================================================================================================================

    private Connection connect() throws SQLException {
        Connection connection = connections.get();
        if (connection == null) throw new SQLException("No database connection");
        connection.setAutoCommit(false);
        return connection;
    }

    private Snapshot read(Connection connection, String guildId) throws SQLException {
        List<QueryRecord> guilds = query(connection, "SELECT * FROM guild WHERE guild_id = ?", guildId);
        QueryRecord guild = guilds.isEmpty() ? new QueryRecord() : guilds.get(0);
        return new Snapshot(guild,
            query(connection, "SELECT * FROM channel WHERE guild_id = ? ORDER BY id", guildId),
            readAlerts(connection, guildId),
            query(connection, "SELECT * FROM automated_action WHERE guild_id = ? ORDER BY id", guildId));
    }

    private List<AlertData> readAlerts(Connection connection, String guildId) throws SQLException {
        List<QueryRecord> rows = query(connection,
            "SELECT a.id AS alert_id, a.message, a.private_message, a.channel, a.enabled, a.send_type, a.type, "
                + "r.level, r.temporary, t.streamer_id, t.role_id FROM alert a "
                + "LEFT JOIN alert_reward r ON r.alert_id = a.id LEFT JOIN alert_twitch t ON t.alert_id = a.id "
                + "WHERE a.guild_id = ? ORDER BY a.id", guildId);
        Map<Integer, HashMap<Integer, String>> roles = new HashMap<>();
        for (QueryRecord row : query(connection,
                "SELECT r.id, r.alert_id, r.role_id FROM alert_role r JOIN alert a ON a.id = r.alert_id WHERE a.guild_id = ? ORDER BY r.id", guildId)) {
            roles.computeIfAbsent(row.getAsInt("alert_id"), ignored -> new HashMap<>())
                .put(row.getAsInt("id"), row.get("role_id"));
        }
        List<AlertData> alerts = new ArrayList<>();
        for (QueryRecord row : rows) {
            HashMap<Integer, String> alertRoles = roles.get(row.getAsInt("alert_id"));
            alerts.add(switch (AlertType.getFromOrdinal(row.getAsInt("type"))) {
                case REWARD -> new RewardData(row, alertRoles);
                case TWITCH -> new TwitchData(row);
                default -> new AlertData(row, alertRoles);
            });
        }
        return alerts;
    }

    private void lockGuild(Connection connection, String guildId) throws SQLException {
        execute(connection, "INSERT INTO guild (guild_id, prefix) VALUES (?, ?) ON DUPLICATE KEY UPDATE guild_id = guild_id", guildId, getDefaultPrefix());
        query(connection, "SELECT guild_id FROM guild WHERE guild_id = ? FOR UPDATE", guildId);
    }

    private void update(Connection connection, String table, Map<String, String> columns, Map<String, Object> values,
            String where, Object... identity) throws SQLException {
        StringJoiner assignments = new StringJoiner(", ");
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = columns.get(entry.getKey());
            if (column == null) continue;
            assignments.add(column + " = ?");
            parameters.add(entry.getValue());
        }
        if (parameters.isEmpty()) return;
        for (Object value : identity) parameters.add(value);
        execute(connection, "UPDATE " + table + " SET " + assignments + " WHERE " + where, parameters.toArray());
    }

    private int insert(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated ID");
                return keys.getInt(1);
            }
        }
    }

    private void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private List<QueryRecord> query(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<QueryRecord> rows = new ArrayList<>();
                while (result.next()) {
                    QueryRecord row = new QueryRecord();
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++)
                        row.put(result.getMetaData().getColumnLabel(column).toLowerCase(java.util.Locale.ROOT), result.getObject(column));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
    }
}
