package com.safjnest.database.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import com.safjnest.database.sql.AbstractDB;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.QueryRecordParser;
import com.safjnest.discord.auth.ApiKey;

public final class AuthDB extends AbstractDB {

    private static final String ACTIVE_SESSION = """
        FROM auth_session s
        JOIN api_key k ON k.id = s.api_key_id
        WHERE s.token_hash = ? AND s.api_key_id = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND k.active = 1
        """;

    private static final AuthDB instance = new AuthDB();

    private final Supplier<Connection> connections;

    private AuthDB() { this.connections = this::getConnection; }

    public AuthDB(Supplier<Connection> connections) { this.connections = connections; }

    public static AuthDB get() { return instance; }

    @Override
    protected String getDatabase() { return "auth"; }
    public record Session(String clientId, String discordId, String encryptedToken, String userJson, long expiresAt) {}

    public void createTransaction(String stateHash, String clientId, String redirectUri, long expiresAt) {
        String sql = "INSERT INTO auth_transaction (state_hash,api_key_id,redirect_uri,expires_at) VALUES (?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try {
                statement.setString(1, stateHash); statement.setString(2, clientId);
                statement.setString(3, redirectUri); statement.setLong(4, expiresAt);
                statement.executeUpdate(); connection.commit();
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot save login transaction"); }
    }

    public boolean consumeTransaction(String stateHash, String clientId, String redirectUri, long now) {
        String sql = "DELETE FROM auth_transaction WHERE state_hash=? AND api_key_id=? AND redirect_uri=? AND expires_at>?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try {
                statement.setString(1, stateHash); statement.setString(2, clientId);
                statement.setString(3, redirectUri); statement.setLong(4, now);
                int changed = statement.executeUpdate(); connection.commit(); return changed == 1;
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot consume login transaction"); }
    }

    public int cleanupTransactions(long now) {
        String sql = "DELETE FROM auth_transaction WHERE expires_at<=? LIMIT 1000";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try { statement.setLong(1, now); int changed = statement.executeUpdate(); connection.commit(); return changed; }
            catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot clean up login transactions"); }
    }

    public ApiKey findClient(String hash) {
        String sql = "SELECT id, name, redirect_uris, type, discord_user_id, user_json FROM api_key WHERE key_hash = ? AND active = 1";

        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next())
                    return null;

                QueryRecord row = QueryRecordParser.fromRow(rows);
                ApiKey.Type type = switch (row.get("type")) {
                    case "generic" -> ApiKey.Type.GENERIC;
                    case "personal" -> ApiKey.Type.PERSONAL;
                    case null, default -> null;
                };

                if (type == null)
                    return null;

                return new ApiKey(row.get("id"), row.get("name"), row.get("redirect_uris"), type,
                    row.get("discord_user_id"), row.get("user_json"), null, hash);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Authentication database unavailable");
        }
    }

    public boolean sessionActive(String hash, String clientId, long now) {
        String sql = "SELECT 1 " + ACTIVE_SESSION;

        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setString(2, clientId);
            statement.setLong(3, now);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Authentication database unavailable");
        }
    }

    public String generate(String name, String redirectUris) {
        ApiKey key = ApiKey.generate(name, redirectUris, null);
        createKey(key.toHash(), key);
        return key.value();
    }

    public String generatePersonal(String name, com.safjnest.discord.auth.DiscordApi.AuthUser user) {
        if (user == null) throw new IllegalArgumentException("A verified Discord user is required");
        ApiKey key = ApiKey.generate(name, null, user);
        createKey(key.toHash(), key);
        return key.value();
    }

    public void createClient(String id, String name, String hash, String redirectUris) {
        createKey(hash, new ApiKey(id, name, redirectUris));
    }

    public void createPersonalKey(String id, String name, String hash, String discordId, String userJson) {
        createKey(hash, new ApiKey(id, name, "", ApiKey.Type.PERSONAL, discordId, userJson, null, hash));
    }

    public String revokeClient(String name) {
        return revokeKey(name, "generic");
    }

    public boolean revokePersonalKey(String name) {
        return revokeKey(name, "personal") != null;
    }

    public boolean revokePersonalSession(String id) {
        String sql = "UPDATE api_key SET active=0,revoked_at=CURRENT_TIMESTAMP(3) WHERE id=? AND type='personal' AND active=1";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try {
                int changed = statement.executeUpdate();
                connection.commit();
                return changed > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot revoke personal key"); }
    }

    public void createSession(String hash, Session session) {
        String sql = "INSERT INTO auth_session (token_hash,api_key_id,discord_user_id,encrypted_discord_token,user_json,expires_at) VALUES (?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash); statement.setString(2, session.clientId()); statement.setString(3, session.discordId());
            statement.setString(4, session.encryptedToken()); statement.setString(5, session.userJson()); statement.setLong(6, session.expiresAt());
            try { statement.executeUpdate(); connection.commit(); }
            catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot save session"); }
    }

    public Session findSession(String hash, String clientId, long now) {
        String sql = "SELECT s.api_key_id, s.discord_user_id, s.encrypted_discord_token, s.user_json, s.expires_at " + ACTIVE_SESSION;

        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setString(2, clientId);
            statement.setLong(3, now);

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next())
                    return null;

                QueryRecord row = QueryRecordParser.fromRow(rows);
                long expiresAt = Long.parseLong(row.get("expires_at"));

                return new Session(row.get("api_key_id"), row.get("discord_user_id"), row.get("encrypted_discord_token"),
                    row.get("user_json"), expiresAt);
            }
        } catch (SQLException | NumberFormatException exception) {
            throw new IllegalStateException("Authentication database unavailable");
        }
    }

    public boolean revokeSession(String hash, String clientId) {
        String sql = "UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE token_hash=? AND api_key_id=? AND revoked_at IS NULL";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try { statement.setString(1, hash); statement.setString(2, clientId); int changed = statement.executeUpdate(); connection.commit(); return changed > 0; }
            catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot revoke session"); }
    }

    public int cleanupSessions(long now) {
        String sql = "DELETE FROM auth_session WHERE revoked_at IS NOT NULL OR expires_at<=? OR api_key_id IN (SELECT id FROM api_key WHERE active=0) LIMIT 1000";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try { statement.setLong(1, now); int changed = statement.executeUpdate(); connection.commit(); return changed; }
            catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot clean up sessions"); }
    }

    // ================================================================================================================

    private void createKey(String hash, ApiKey key) {
        String sql = "INSERT INTO api_key (id,name,key_hash,redirect_uris,type,discord_user_id,user_json) VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.id());
            statement.setString(2, key.name());
            statement.setString(3, hash);
            statement.setString(4, key.redirectUris());
            statement.setString(5, key.type().name().toLowerCase(java.util.Locale.ROOT));
            statement.setString(6, key.toUserId());
            statement.setString(7, key.userJson());
            try {
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot create API key"); }
    }

    private String revokeKey(String name, String type) {
        String sql = "UPDATE api_key SET active=0,revoked_at=CURRENT_TIMESTAMP(3) WHERE name=? AND type=? AND active=1";
        try (Connection connection = connection();
                PreparedStatement lookup = connection.prepareStatement("SELECT key_hash FROM api_key WHERE name=? AND type=? AND active=1 FOR UPDATE");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            try {
                lookup.setString(1, name);
                lookup.setString(2, type);
                String hash;
                try (ResultSet rows = lookup.executeQuery()) { hash = rows.next() ? rows.getString(1) : null; }
                if (hash == null) { connection.rollback(); return null; }
                statement.setString(1, name);
                statement.setString(2, type);
                statement.executeUpdate();
                connection.commit();
                return hash;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw new IllegalStateException("Cannot revoke API key"); }
    }

    private Connection connection() throws SQLException {
        Connection connection = connections.get();
        if (connection == null) throw new SQLException("Connection unavailable");
        return connection;
    }
}
