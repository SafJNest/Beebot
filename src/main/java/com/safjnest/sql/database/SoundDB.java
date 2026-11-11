package com.safjnest.sql.database;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import com.safjnest.auth.AuthException;
import com.safjnest.sql.QueryRecord;
import org.springframework.http.HttpStatus;

public class SoundDB {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundDB.class);
    private static final int BATCH_SIZE = 500;

    private final Supplier<Connection> connections;
    private final ThreadLocal<Session> readSession = new ThreadLocal<>();
    private static final int HISTORY_FETCH_SIZE = 256;

    public record Play(String id, String soundId, String userId, String guildId, Instant time, int source) {}

    public record Selection(String userId, Set<String> guilds, String guildId, boolean mine,
    String q, String tag, String sort, String order, int limit) {
    }

    public SoundDB() {
        this(() -> BotDB.get().getConnection());
    }

    public SoundDB(Supplier<Connection> connections) {
        this.connections = connections;
    }

    public List<String> select(Selection selection) {
        List<Object> args = new ArrayList<>();
        String where;
        if (selection.guildId() != null) {
            where = "s.guild_id = ?";
            args.add(selection.guildId());
        }
        else if (selection.mine()) {
            where = "s.user_id = ?";
            args.add(selection.userId());
        }
        else {
            where = visibility(selection.userId(), selection.guilds(), args);
        }
        if (!selection.q().isEmpty()) {
            where += " AND LOWER(s.name) LIKE ? ESCAPE '!'";
            args.add("%" + escape(selection.q()) + "%");
        }
        if (!selection.tag().isEmpty()) {
            where += " AND EXISTS (SELECT 1 FROM tag_sounds ts JOIN tag t ON t.id = ts.tag_id WHERE ts.sound_id = s.id AND LOWER(t.name) = ?)";
            args.add(selection.tag());
        }
        String sort = switch (selection.sort()) {
            case "likes" -> "likes";
            case "dislikes" -> "dislikes";
            default -> "plays";
        };
        String order = selection.order().equals("asc") ? "ASC" : "DESC";
        String sql = "SELECT s.id FROM sound s WHERE " + where + " ORDER BY s." + sort + " " + order + ", s.id ASC";
        if (selection.limit() > 0) {
            sql += " LIMIT ?";
            args.add(selection.limit());
        }
        List<String> result = new ArrayList<>();
        for (QueryRecord row : read(sql, args.toArray())) result.add(row.get("id"));
        return result;
    }

    public List<QueryRecord> sounds(Collection<String> ids) {
        List<QueryRecord> result = new ArrayList<>();
        batches(ids, batch -> result.addAll(read("SELECT * FROM sound WHERE id IN (" + marks(batch.size()) + ")", batch.toArray())));
        return result;
    }

    public List<QueryRecord> tags(Collection<String> ids) {
        List<QueryRecord> result = new ArrayList<>();
        batches(ids, batch -> result.addAll(read("SELECT DISTINCT ts.sound_id, t.id AS tag_id, t.name FROM tag_sounds ts JOIN tag t ON t.id = ts.tag_id WHERE ts.sound_id IN (" + marks(batch.size()) + ") ORDER BY t.name, t.id", batch.toArray())));
        return result;
    }

    public List<QueryRecord> searchTags(String q, String userId, Set<String> guilds) {
        List<Object> args = new ArrayList<>();
        args.add(escape(q) + "%");
        String visible = visibility(userId, guilds, args);
        return read("SELECT DISTINCT t.id, t.name FROM tag t JOIN tag_sounds ts ON ts.tag_id=t.id JOIN sound s ON s.id=ts.sound_id WHERE LOWER(t.name) LIKE ? ESCAPE '!' AND " + visible + " ORDER BY t.name, t.id LIMIT 100", args.toArray());
    }

    public List<QueryRecord> enrichment(Collection<String> ids, String userId) {
        List<QueryRecord> result = new ArrayList<>();
        batches(ids, batch -> {
            List<Object> args = new ArrayList<>();
            args.add(userId);
            args.add(userId);
            args.addAll(batch);
            result.addAll(read("SELECT s.id, (SELECT COUNT(DISTINCT h.user_id) FROM sound_history h WHERE h.sound_id=s.id) AS listeners, "
            + "(SELECT COUNT(*) FROM sound_history h WHERE h.sound_id=s.id AND h.user_id=?) AS my_plays, "
            + "(SELECT value FROM sound_interactions i WHERE i.sound_id=s.id AND i.user_id=?) AS vote, "
            + "(SELECT COUNT(DISTINCT bs.id) FROM soundboard_sounds bs WHERE bs.sound_id=s.id) AS boards "
            + "FROM sound s WHERE s.id IN (" + marks(batch.size()) + ")", args.toArray()));
        });
        return result;
    }

    public <T> T withReadSession(Supplier<T> work) {
        if (readSession.get() != null) return work.get();
        try (Session session = connect()) {
            session.connection().setAutoCommit(false);
            session.connection().setReadOnly(true);
            session.connection().setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            readSession.set(session);
            try { return work.get(); }
            finally {
                readSession.remove();
                session.connection().rollback();
            }
        } catch (SQLException exception) { throw unavailable(exception); }
    }

    public void forEachPlay(Collection<String> ids, String guildId, Instant from, Instant to,
            java.util.function.Consumer<Play> consumer) {
        if (ids.isEmpty()) return;
        withReadSession(() -> {
            Connection connection = readSession.get().connection();
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            long started = System.nanoTime();
            long rowsRead = 0;
            try {
                for (List<String> batch : partition(ids)) {
                    List<Object> args = new ArrayList<>(batch);
                    String where = "sound_id IN (" + marks(batch.size()) + ") AND time < ?";
                    args.add(Timestamp.from(to));
                    if (from != null) { where += " AND time >= ?"; args.add(Timestamp.from(from)); }
                    if (guildId != null) { where += " AND guild_id = ?"; args.add(guildId); }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, sound_id, user_id, guild_id, time, source FROM sound_history WHERE " + where,
                            ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                        statement.setFetchSize(HISTORY_FETCH_SIZE);
                        bind(statement, args.toArray());
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) {
                                consumer.accept(new Play(rows.getString("id"), rows.getString("sound_id"), rows.getString("user_id"),
                                    rows.getString("guild_id"), rows.getTimestamp("time", utc).toInstant(), rows.getInt("source")));
                                rowsRead++;
                            }
                        }
                    }
                }
                return null;
            } catch (SQLException exception) { throw unavailable(exception); }
            finally { LOG.debug("Sound history: {} rows processed in {} ms", rowsRead, (System.nanoTime() - started) / 1_000_000); }
        });
    }

    public List<QueryRecord> greets(String userId) {
        return read("SELECT guild_id, sound_id FROM greeting WHERE user_id=?", userId);
    }

    public void patch(String id, String userId, Map<String, Object> values, List<String> tags) {
        transaction(connection -> {
            QueryRecord sound = lock(connection, id);
            if (!userId.equals(sound.get("user_id"))) throw forbidden();
            if (values.containsKey("name")) {
                String name = values.get("name").toString();
                if (!query(connection, "SELECT id FROM sound WHERE id<>? AND name=? AND (guild_id=? OR user_id=?)", id, name, sound.get("guild_id"), userId).isEmpty())
                throw new AuthException(HttpStatus.BAD_REQUEST, "duplicate_sound_name", "Sound name is already in use");
            }
            for (var entry : values.entrySet()) {
                if (!Set.of("name", "public").contains(entry.getKey())) throw new IllegalArgumentException("Unknown column");
                execute(connection, "UPDATE sound SET `" + entry.getKey() + "`=? WHERE id=?", entry.getValue(), id);
            }
            if (tags != null) {
                execute(connection, "DELETE FROM tag_sounds WHERE sound_id=?", id);
                for (String name : tags) {
                    List<QueryRecord> existing = query(connection, "SELECT id FROM tag WHERE name=? ORDER BY id LIMIT 1", name);
                    String tagId;
                    if (existing.isEmpty()) {
                        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tag(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                            statement.setString(1, name);
                            statement.executeUpdate();
                            try (ResultSet keys = statement.getGeneratedKeys()) {
                                keys.next();
                                tagId = keys.getString(1);
                            }
                        }
                    }
                    else tagId = existing.get(0).get("id");
                    execute(connection, "INSERT INTO tag_sounds(sound_id,tag_id) VALUES (?,?)", id, tagId);
                }
            }
        });
    }

    public void vote(String id, String userId, Set<String> guilds, int value, boolean enabled) {
        transaction(connection -> {
            QueryRecord sound = lock(connection, id);
            if (!visible(sound, userId, guilds)) throw missing();
            List<QueryRecord> old = query(connection, "SELECT value FROM sound_interactions WHERE sound_id=? AND user_id=?", id, userId);
            int current = old.isEmpty() ? 0 : old.get(0).getAsInt("value");
            int next = enabled ? value : current == value ? 0 : current;
            if (old.isEmpty()) execute(connection, "INSERT INTO sound_interactions(sound_id,user_id,value) VALUES (?,?,?)", id, userId, next);
            else execute(connection, "UPDATE sound_interactions SET value=? WHERE sound_id=? AND user_id=?", next, id, userId);
            execute(connection, "UPDATE sound SET likes=(SELECT COUNT(*) FROM sound_interactions WHERE sound_id=? AND value=1), dislikes=(SELECT COUNT(*) FROM sound_interactions WHERE sound_id=? AND value=-1) WHERE id=?", id, id, id);
        });
    }

    public QueryRecord delete(String id, String userId, Set<String> administrators) {
        QueryRecord[] removed = new QueryRecord[1];
        transaction(connection -> {
            QueryRecord row = lock(connection, id);
            if (!userId.equals(row.get("user_id")) && !administrators.contains(row.get("guild_id"))) throw forbidden();
            execute(connection, "DELETE FROM sound WHERE id=?", id);
            removed[0] = row;
        });
        return removed[0];
    }

    public void greet(String userId, String guildId, String soundId) {
        transaction(connection -> {
            if (soundId == null) {
                execute(connection, "DELETE FROM greeting WHERE user_id=? AND guild_id=?", userId, guildId);
                return;
            }
            QueryRecord sound = lock(connection, soundId);
            boolean allowed = guildId.equals("0") ? sound.getAsBoolean("public") || userId.equals(sound.get("user_id"))
            : sound.getAsBoolean("public") || guildId.equals(sound.get("guild_id"));
            if (!allowed) throw new AuthException(HttpStatus.BAD_REQUEST, "private_sound_wrong_guild", "Private sound cannot be used in this context");
            execute(connection, "INSERT INTO greeting(user_id,guild_id,sound_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE sound_id=VALUES(sound_id)", userId, guildId, soundId);
        });
    }

    public void play(String id, String userId, String guildId, int source) {
        transaction(connection -> {
            lock(connection, id);
            execute(connection, "INSERT INTO sound_history(user_id,sound_id,guild_id,source) VALUES (?,?,?,?)", userId, id, guildId, source);
            execute(connection, "UPDATE sound SET plays=plays+1 WHERE id=?", id);
        });
    }

    public static boolean visible(QueryRecord sound, String userId, Set<String> guilds) {
        return sound.getAsBoolean("public") || userId.equals(sound.get("user_id")) || guilds.contains(sound.get("guild_id"));
    }

    public List<QueryRecord> read(String sql, Object... args) {
        Session existing = readSession.get();
        if (existing != null) {
            try { return query(existing.connection(), sql, args); }
            catch (SQLException exception) { throw unavailable(exception); }
        }
        try (Session session = connect()) {
            Connection connection = session.connection();
            return query(connection, sql, args);
        }
        catch (SQLException exception) {
            throw unavailable(exception);
        }
    }
    // ================================================================================================================

    private String visibility(String user, Set<String> guilds, List<Object> args) {
        args.add(user);
        String where = "(s.public=1 OR s.user_id=?";
        if (!guilds.isEmpty()) {
            where += " OR s.guild_id IN (" + marks(guilds.size()) + ")";
            args.addAll(guilds);
        }
        return where + ")";
    }

    private record Session(Connection connection, String timeZone) implements AutoCloseable {
        public void close() throws SQLException {
            try {
                if (timeZone != null) {
                    try (PreparedStatement statement = connection.prepareStatement("SET time_zone = ?")) {
                        statement.setString(1, timeZone);
                        statement.execute();
                    }
                }
            } finally { connection.close(); }
        }
    }

    private Session connect() throws SQLException {
        Connection connection = connections.get();
        if (connection == null) throw new SQLException("No database connection");
        try {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            String originalZone = null;
            if (product.contains("mariadb") || product.contains("mysql")) {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet result = statement.executeQuery("SELECT @@session.time_zone")) {
                        result.next();
                        originalZone = result.getString(1);
                    }
                    statement.execute("SET time_zone = '+00:00'");
                }
            }
            return new Session(connection, originalZone);
        } catch (SQLException | RuntimeException exception) {
            connection.close();
            throw exception;
        }
    }

    private interface Work {
        void run(Connection connection) throws SQLException;
    }

    private void transaction(Work work) {
        try (Session session = connect()) {
            Connection connection = session.connection();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            }
            catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
        catch (SQLException exception) {
            throw unavailable(exception);
        }
    }

    private QueryRecord lock(Connection connection, String id) throws SQLException {
        List<QueryRecord> rows = query(connection, "SELECT * FROM sound WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw missing();
        return rows.get(0);
    }

    private List<QueryRecord> query(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                List<QueryRecord> rows = new ArrayList<>();
                while (result.next()) {
                    QueryRecord row = new QueryRecord();
                    for (int i=1; i<=result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),
                    result.getMetaData().getColumnType(i) == Types.TIMESTAMP
                    ? result.getTimestamp(i, Calendar.getInstance(TimeZone.getTimeZone("UTC"))) : result.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void execute(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object[] args) throws SQLException {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (int i=0; i<args.length; i++) {
            if (args[i] instanceof Timestamp timestamp) statement.setTimestamp(i+1, timestamp, utc);
            else statement.setObject(i+1, args[i]);
        }
    }

    private List<List<String>> partition(Collection<String> ids) {
        List<String> list = new ArrayList<>(new LinkedHashSet<>(ids));
        List<List<String>> batches = new ArrayList<>();
        for (int i=0; i<list.size(); i+=BATCH_SIZE) batches.add(list.subList(i, Math.min(i+BATCH_SIZE, list.size())));
        return batches;
    }

    private void batches(Collection<String> ids, java.util.function.Consumer<List<String>> consumer) {
        for (var batch : partition(ids)) consumer.accept(batch);
    }

    private String marks(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String escape(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private AuthException unavailable(Exception cause) {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "sound_storage_unavailable", "Sound storage is unavailable");
    }

    private AuthException missing() {
        return new AuthException(HttpStatus.NOT_FOUND, "sound_not_found", "Sound not found");
    }

    private AuthException forbidden() {
        return new AuthException(HttpStatus.FORBIDDEN, "sound_forbidden", "Sound operation is not permitted");
    }
}
