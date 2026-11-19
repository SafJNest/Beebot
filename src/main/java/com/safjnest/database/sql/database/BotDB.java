package com.safjnest.database.sql.database;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.BlacklistData;
import com.safjnest.bot.model.guild.ChannelData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.bot.model.guild.alert.AlertSendType;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.database.sql.AbstractDB;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.QueryRecordParser;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.ParseUtils;
import com.safjnest.utils.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

/**
 * Useless (now usefull) class but {@link <a href="https://github.com/Leon412">Leon412</a>} is one
 * of the biggest caterpies ever made
 */
public class BotDB extends AbstractDB {
    private static final int CACHE_BATCH_SIZE = 500;
    public static final int MAX_SOUNDBOARD_SOUNDS = 25;

    private static BotDB instance;

    private static final org.slf4j.Logger SOUND_LOG = org.slf4j.LoggerFactory.getLogger(BotDB.class);
    private static final int SOUND_BATCH_SIZE = 500;
    private static final int SOUND_HISTORY_FETCH_SIZE = 256;

    private final Supplier<Connection> connections;
    private final ThreadLocal<SoundSession> soundReadSession = new ThreadLocal<>();
    private final SoundDB soundDB = new SoundDB(this);

    static {
        instance = new BotDB();
    }

    public BotDB() {
        this(null);
    }

    public BotDB(Supplier<Connection> connections) {
        this.connections = connections;
    }

    @Override
    public Connection getConnection() {
        return connections == null ? super.getConnection() : connections.get();
    }

    @Override
	protected String getDatabase() {
        return SettingsLoader.getSettings().getConfig().isTesting() 
            ? SettingsLoader.getSettings().getJsonSettings().getTestDatabase().getDatabaseName()
            :  SettingsLoader.getSettings().getJsonSettings().getDatabase().getDatabaseName();
	}

    public static BotDB get() {
        return instance;
    }

    public SoundDB communitySounds() {
        return soundDB;
    }

    public static List<QueryRecord> getGuildsData(String filter){
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild WHERE " + filter + ";";
        return instance.query(query);
    }

    public static List<Sound> getSounds(String user_id, int page, int limit) {
        List<QueryRecord> res = instance.query("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE user_id = '" + user_id + "' OR public = '1' ORDER BY id ASC LIMIT " + (page-1)*limit + ", " + limit);
        List<QueryRecord> tags = BotDB.getSoundsTags(columnValues(res, "id").toArray(new String[0]));
        
        List<Sound> sounds = new ArrayList<>();
        for(QueryRecord qr : res) {
            List<Tag> tagList = new ArrayList<>();
            for (QueryRecord tag : tags) {
                if (tag.get("sound_id").equals(qr.get("id"))) {
                    tagList.add(new Tag(tag.getAsInt("tag_id"), tag.get("name")));
                }
            }

            if (tagList.size() != Tag.MAX_TAG_SOUND) {
                for (int i = tagList.size(); i < Tag.MAX_TAG_SOUND; i++) {
                    tagList.add(new Tag());
                }
            }
            sounds.add(new Sound(qr, tagList));
        }

        return sounds;
    }


    public static List<QueryRecord> getlistGuildSounds(String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static List<QueryRecord> getlistGuildSounds(String guild_id, int limit) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT " + limit);
    }

    public static List<QueryRecord> getlistGuildSounds(String guild_id, String orderBy) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY " + orderBy +" ASC ");
    }



    public static List<QueryRecord> getGuildRandomSound(String guild_id){
        return instance.query("SELECT name, id FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static List<QueryRecord> getUserSound(String user_id){
        return instance.query("SELECT name, id, guild_id, extension FROM sound WHERE user_id = '" + user_id + "';");
    }

    public static List<QueryRecord> getlistUserSounds(String user_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY name ASC");
    }

    public static List<QueryRecord> getlistUserSoundsTime(String user_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY time ASC");
    }

    public static List<QueryRecord> getlistUserSounds(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY name ASC");
    }

    public static List<QueryRecord> getlistUserSoundsTime(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY time ASC");
    }

    public static List<QueryRecord> getFocusedGuildSound(String guild_id, String like){
        return instance.query("SELECT name, id FROM sound WHERE name LIKE '" + like + "%' AND guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static List<QueryRecord> getFocusedUserSound(String user_id, String like){
        return instance.query("SELECT name, id, guild_id FROM sound WHERE (name LIKE '" + like + "%' OR id LIKE '" + like + "%') AND user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static List<QueryRecord> getUserGuildSounds(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' OR guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static List<QueryRecord> getFocusedListUserSounds(String user_id, String guild_id, String like) {
        return instance.query("SELECT name, id, guild_id, extension FROM sound WHERE name LIKE '" + like + "%' OR id LIKE '" + like + "%' AND (user_id = '" + user_id + "' OR guild_id = '" + guild_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public List<String> findSoundIds(String lookup, String guildId, String userId) {
        if (lookup == null || lookup.isBlank())
            return List.of();

        boolean byId = lookup.matches("[0-9]+");
        List<Object> arguments = new ArrayList<>();
        if (byId) {
            int id = ParseUtils.parseInt(lookup);
            if (id <= 0)
                return List.of();

            arguments.add(id);
        } else {
            arguments.add(lookup);
        }
        String visibility = soundVisibility(userId, Set.of(guildId), arguments);
        String column = byId ? "s.id" : "s.name";

        return soundIds(readSoundRows("SELECT s.id FROM sound s WHERE " + column + " = ? AND " + visibility
            + " ORDER BY s.id", arguments.toArray()));
    }

    public List<String> searchSoundIds(String query, String author, String guildId, String userId) {
        if (query == null || query.isBlank())
            return List.of();

        List<Object> arguments = new ArrayList<>();
        arguments.add("%" + escapeSoundQuery(query) + "%");
        String visibility = soundVisibility(userId, Set.of(guildId), arguments);
        String owner = "";
        if (author != null) {
            owner = " AND s.user_id = ?";
            arguments.add(author);
        }

        return soundIds(readSoundRows("SELECT s.id FROM sound s WHERE s.name LIKE ? ESCAPE '!' AND " + visibility + owner
            + " ORDER BY s.plays DESC, s.likes DESC, s.id LIMIT 25", arguments.toArray()));
    }

    public String insertSound(String name, String description, String guildId, String userId, String extension, boolean isPublic,
            long durationMs, List<String> tags, Function<Sound, String> storeFile) {
        String sql = "INSERT INTO sound(name, description, guild_id, user_id, extension, public, time, duration_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String id;

        try (SoundSession session = connectSound()) {
            Connection connection = session.connection();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (!soundQuery(connection, "SELECT id FROM sound WHERE name = ? AND (guild_id = ? OR user_id = ?)",
                        name, guildId, userId).isEmpty())
                    throw new Exception(Type.INVALID_REQUEST, "duplicate_sound_name", "Sound name is already in use");

                bindSound(statement, new Object[] {name, description, guildId, userId, extension, isPublic, Timestamp.from(Instant.now()), durationMs});
                if (statement.executeUpdate() != 1)
                    throw new SQLException("Sound insert did not create one row");

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next() || keys.getLong(1) <= 0)
                        throw new SQLException("Sound insert did not return an ID");

                    id = keys.getString(1);
                }

                List<Tag> savedTags = saveSoundTags(connection, id, tags);
                String key = storeFile.apply(new Sound(lockSound(connection, id), savedTags));
                if (key != null)
                    soundExecute(connection, "UPDATE sound SET url=? WHERE id=?", key, id);

                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }

                throw failure;
            }
        } catch (SQLException exception) {
            throw soundUnavailable(exception);
        }

        com.safjnest.bot.cache.managers.SoundCache.catalogChanged(id);
        return id;
    }

    public static boolean updateSound(String id, String name, boolean isPublic) {
        return com.safjnest.bot.cache.managers.SoundCache.withSound(id, sound -> {
            if (sound == null)
                return false;

            Sound edit = sound.deferEdit();
            edit.setName(name);
            edit.setPublic(isPublic);

            return edit.update();
        });
    }

    public static boolean updateSound(String id, String name) {
        return com.safjnest.bot.cache.managers.SoundCache.withSound(id,
            sound -> sound != null && sound.setName(name));
    }

    public static boolean updateSound(String id, boolean isPublic) {
        return com.safjnest.bot.cache.managers.SoundCache.withSound(id,
            sound -> sound != null && sound.setPublic(isPublic));
    }


    public static boolean updateUserPlays(String soundId, String userId, String guildId, int source) {
        return com.safjnest.bot.cache.managers.SoundCache.withSound(soundId,
            sound -> sound != null && sound.increaseUserPlays(userId, guildId,
                com.safjnest.bot.audio.types.AudioType.values()[source]));
    }

    public static QueryRecord getPlays(String sound_id, String user_id) {
        return instance.readSoundUserPlays(sound_id, user_id);
    }

    public static QueryRecord getGlobalPlays(String sound_id) {
        return instance.lineQuery("SELECT count(id) as times FROM sound_play WHERE sound_id = '" + sound_id + "'");
    }

    public static String getSoundsUploadedByUserCount(String user_id) {
        return instance.lineQuery("select count(name) as count from sound where user_id = '" + user_id + "';").get("count");
    }

    public static String getSoundsUploadedByUserCount(String user_id, String guild_id) {
        return instance.lineQuery("select count(name) as count from sound where guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';").get("count");
    }

    public static String getTotalPlays(String user_id) {
        return instance.lineQuery("select count(id) as sum from sound_play where user_id = '" + user_id + "';").get("sum");
    }

    public static QueryRecord searchSoundboard(String string, String guild_id, String user_id) {
        return instance.lineQuery("SELECT id from soundboard WHERE (ID = '" + string + "' OR name = '" + string + "') AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')");
    }

    public static boolean soundboardExists(String name, String guildId) {
        try (SoundSession session = instance.connectSound()) {
            return !instance.soundQuery(session.connection(), "SELECT id FROM soundboard WHERE name = ? AND guild_id = ?",
                name, guildId).isEmpty();
        } catch (SQLException exception) {
            throw instance.soundUnavailable(exception);
        }
    }

    public static boolean soundboardExists(String id, String guild_id, String user_id) {
        return !instance.lineQuery("SELECT id from soundboard WHERE ID = '" + id + "' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')").isEmpty();
    }

    public static int getSoundInSoundboardCount(String id) {
        return instance.lineQuery("SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'").getAsInt("count");
    }

    public static List<QueryRecord> getSoundsFromSoundBoard(String id) {
        return instance.query("select soundboard_sounds.sound_id as sound_id, sound.extension as extension, sound.name as name, sound.guild_id as guild_id from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + id + "' order by name");
    }

    public record SoundboardData(String name, String thumbnailKey) {}

    public record SoundboardAccess(String guildId, String userId, boolean administrator) {}

    public List<QueryRecord> readSoundboards(String guildId) {
        return readSoundRows("""
            SELECT b.id, b.name, b.guild_id, b.user_id, b.thumbnail, COUNT(s.sound_id) AS sound_count
            FROM soundboard b LEFT JOIN soundboard_sounds s ON b.id = s.id
            WHERE b.guild_id = ? GROUP BY b.id, b.name, b.guild_id, b.user_id, b.thumbnail
            ORDER BY b.name, b.id
            """, guildId);
    }

    public QueryRecord readSoundboard(String guildId, String id) {
        List<QueryRecord> rows = readSoundRows("""
            SELECT b.id, b.name, b.guild_id, b.user_id, b.thumbnail,
                   (SELECT COUNT(*) FROM soundboard_sounds s WHERE s.id = b.id) AS sound_count
            FROM soundboard b WHERE b.guild_id = ? AND b.id = ?
            """, guildId, id);
        if (rows.isEmpty())
            throw soundboardMissing();

        return rows.getFirst();
    }

    public List<QueryRecord> readSoundboardSounds(String id) {
        return readSoundRows("""
            SELECT s.* FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id
            WHERE ss.id = ? ORDER BY s.name, s.id
            """, id);
    }

    public SoundboardData getSoundboardByID(String id) {
        try (SoundSession session = connectSound();
                PreparedStatement statement = session.connection().prepareStatement("SELECT name, thumbnail FROM soundboard WHERE id = ?")) {
            statement.setString(1, id);

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next())
                    return null;

                return new SoundboardData(rows.getString("name"), rows.getString("thumbnail"));
            }
        } catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    public static List<QueryRecord> getRandomSoundboard(String guild_id, String user_id) {
        return instance.query("SELECT name, id, guild_id FROM soundboard WHERE guild_id = '" + guild_id + "' OR user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static List<QueryRecord> getFocusedSoundboard(String guild_id, String user_id, String like){
        return instance.query("SELECT name, id, guild_id FROM soundboard WHERE name LIKE '" + like + "%' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static List<QueryRecord> getFocusedSoundFromSounboard(String id, String like){
        return instance.query("SELECT s.name as name, s.id as sound_id, s.guild_id as guild_id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE s.name LIKE '" + like + "%' AND ss.id = '" + id + "' ORDER BY RAND() LIMIT 25;");
    }

    public String insertSoundBoard(String name, String guildId, String userId, Function<String, String> uploadThumbnail,
            String... soundIds) {
        return insertSoundboard(name, guildId, userId, uploadThumbnail, List.of(soundIds), false);
    }

    public String createSoundboard(String name, SoundboardAccess access, List<String> soundIds) {
        return insertSoundboard(name, access.guildId(), access.userId(), null, soundIds, true);
    }

    public void renameSoundboard(String id, SoundboardAccess access, String name) {
        soundTransaction(connection -> {
            lockSoundboardGuild(connection, access.guildId());
            QueryRecord current = lockSoundboard(connection, id, access);
            if (current.get("name").equals(name))
                return;

            requireUniqueSoundboardName(connection, name, access.guildId(), id);
            soundExecute(connection, "UPDATE soundboard SET name = ? WHERE id = ?", name, id);
        });
    }

    public void addSoundToBoard(String id, SoundboardAccess access, String soundId) {
        changeSoundboard(id, access, connection -> {
            List<String> current = soundIds(soundQuery(connection,
                "SELECT sound_id AS id FROM soundboard_sounds WHERE id = ?", id));
            if (current.contains(soundId))
                return;
            if (current.size() >= MAX_SOUNDBOARD_SOUNDS)
                throw new Exception(Type.CONFLICT, "soundboard_full", "Soundboard is full");

            requireSoundboardSound(connection, soundId, access.guildId());
            soundExecute(connection, "INSERT INTO soundboard_sounds (id, sound_id) VALUES (?, ?)", id, soundId);
        });
    }

    public void removeSoundFromBoard(String id, SoundboardAccess access, String soundId) {
        changeSoundboard(id, access, connection ->
            soundExecute(connection, "DELETE FROM soundboard_sounds WHERE id = ? AND sound_id = ?", id, soundId));
    }

    public void removeSoundboard(String id, SoundboardAccess access) {
        changeSoundboard(id, access, connection -> soundExecute(connection, "DELETE FROM soundboard WHERE id = ?", id));
    }

    public String replaceSoundboardThumbnail(String id, SoundboardAccess access, Function<String, String> upload) {
        String[] key = new String[1];
        changeSoundboard(id, access, connection -> {
            key[0] = upload.apply(id);
            soundExecute(connection, "UPDATE soundboard SET thumbnail = ? WHERE id = ?", key[0], id);
        });

        return key[0];
    }

    private String insertSoundboard(String name, String guildId, String userId, Function<String, String> uploadThumbnail,
            List<String> soundIds, boolean webRequest) {
        if (soundIds.isEmpty() || soundIds.size() > MAX_SOUNDBOARD_SOUNDS)
            throw Exception.invalidRequest("Soundboard requires 1 to 20 sounds");

        try (SoundSession session = connectSound()) {
            Connection connection = session.connection();
            connection.setAutoCommit(false);

            try {
                if (webRequest) {
                    lockSoundboardGuild(connection, guildId);
                    requireUniqueSoundboardName(connection, name, guildId, null);
                    List<String> orderedIds = new ArrayList<>(soundIds);
                    Collections.sort(orderedIds);
                    for (String soundId : orderedIds)
                        requireSoundboardSound(connection, soundId, guildId);
                }

                String id = insertSoundboardRow(connection, name, guildId, userId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO soundboard_sounds (id, sound_id) VALUES (?, ?)")) {
                    for (String soundId : soundIds) {
                        statement.setString(1, id);
                        statement.setString(2, soundId);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                if (uploadThumbnail != null) {
                    String key = uploadThumbnail.apply(id);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE soundboard SET thumbnail = ? WHERE id = ?")) {
                        statement.setString(1, key);
                        statement.setString(2, id);
                        if (statement.executeUpdate() != 1)
                            throw new SQLException("Soundboard thumbnail update failed");
                    }
                }

                connection.commit();

                return id;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }

                throw failure;
            }
        } catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    public boolean updateSoundboardThumbnail(String id, String guildId, String userId, String key) {
        return writeSoundboardThumbnail(
            "UPDATE soundboard SET thumbnail = ? WHERE id = ? AND (guild_id = ? OR user_id = ?)",
            key, id, guildId, userId);
    }

    public static boolean insertSoundsInSoundBoard(String id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids) {
            sb.append("('" + id + "', '" + sound_id + "'), ");
        }
        sb.setLength(sb.length() - 2);

        return instance.defaultQuery("INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + "; ");
    }

    public static boolean deleteSoundboard(String id) {
        return instance.defaultQuery("DELETE FROM soundboard WHERE id = '" + id + "'");
    }

    public static boolean deleteSoundFromSoundboard(String id, String sound_id) {
        return instance.defaultQuery("DELETE FROM soundboard_sounds WHERE id = '" + id + "' AND sound_id = '" + sound_id + "'");
    }

    public static QueryRecord getDefaultVoice(String guild_id) {
        return instance.lineQuery("SELECT name_tts, language_tts FROM guild WHERE guild_id = '" + guild_id + "';");
    }

    public static List<QueryRecord> getGuildData(){
        String query = "SELECT guild_id, prefix, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild;";
        return instance.query(query);
    }

    public static QueryRecord getGuildData(String guild_id) {
        String query = "SELECT guild_id, PREFIX, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id = '" + guild_id + "';";
        return instance.lineQuery(query);
    }

    public static List<QueryRecord> getGuildData(Collection<String> guildIds) {
        return instance.readGuildData(guildIds);
    }

    public List<QueryRecord> readGuildData(Collection<String> guildIds) {
        return cacheRows("SELECT guild_id, prefix, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id IN (%s)", guildIds);
    }

    public static List<QueryRecord> getChannelData(Collection<String> guildIds) {
        return instance.readGuildChannels(guildIds);
    }

    public List<QueryRecord> readGuildChannels(Collection<String> guildIds) {
        return cacheRows("SELECT id, channel_id, guild_id, exp_enabled, exp_modifier, stats_enabled, league_shard FROM channel WHERE guild_id IN (%s)", guildIds);
    }

    public static List<QueryRecord> getWarnings(Collection<String> guildIds) {
        return instance.readGuildActions(guildIds);
    }

    public List<QueryRecord> readGuildActions(Collection<String> guildIds) {
        return cacheRows("SELECT id, guild_id, action, action_role, action_time, infractions, infractions_time FROM automated_action WHERE guild_id IN (%s) ORDER BY infractions DESC", guildIds);
    }

    public static List<QueryRecord> getAliases(Collection<String> userIds) {
        return instance.readUserAliases(userIds);
    }

    public List<QueryRecord> readUserAliases(Collection<String> userIds) {
        return cacheRows("SELECT id, user_id, name, command FROM alias WHERE user_id IN (%s)", userIds);
    }

    public int saveUserAlias(String userId, String name, String command) {
        return guildTransaction(false, connection -> guildInsert(connection,
            "INSERT INTO alias(user_id, name, command) VALUES (?, ?, ?)", userId, name, command));
    }

    public boolean deleteUserAlias(String userId, int id) {
        return guildTransaction(false, connection -> guildExecute(connection,
            "DELETE FROM alias WHERE user_id = ? AND id = ?", userId, id) == 1);
    }

    public List<QueryRecord> readGreet(String userId, String scope) {
        return readSoundRows("SELECT guild_id, sound_id FROM greeting WHERE user_id = ? AND guild_id = ?", userId, scope);
    }

    public QueryRecord readMember(String guildId, String userId) {
        return guildTransaction(true, connection -> {
            List<QueryRecord> rows = guildQuery(connection,
                "SELECT id, user_id, guild_id, experience, level, messages, update_time FROM member WHERE guild_id = ? AND user_id = ?",
                guildId, userId);

            return rows.isEmpty() ? new QueryRecord() : rows.get(0);
        });
    }

    public String saveMember(String guildId, String userId, String prefix,
            int experience, int level, int messages, int updateTime) {
        return guildTransaction(false, connection -> {
            lockGuild(connection, guildId, prefix);

            List<QueryRecord> rows = guildQuery(connection,
                "SELECT id FROM member WHERE guild_id = ? AND user_id = ? FOR UPDATE", guildId, userId);
            String id = rows.isEmpty()
                ? String.valueOf(guildInsert(connection, "INSERT INTO member(guild_id, user_id) VALUES (?, ?)", guildId, userId))
                : rows.get(0).get("id");

            guildExecute(connection, "UPDATE member SET experience = ?, level = ?, messages = ?, update_time = ? WHERE id = ?",
                experience, level, messages, updateTime, id);

            return id;
        });
    }

    public static boolean updateVoiceGuild(String guild_id, String language, String voice) {
        String query = "INSERT INTO guild (guild_id, language_tts, name_tts) VALUES ('" + guild_id + "', '" + language + "', '" + voice + "') ON DUPLICATE KEY UPDATE language_tts = '" + language + "', name_tts = '" + voice + "'";
        return instance.defaultQuery(query);
    }

    public static boolean insertGuild(String guild_id, String prefix) {
        String query = "INSERT INTO guild (guild_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guild_id + "', '" + prefix + "', '1', '0', null) ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';";
        return instance.defaultQuery(query);
    }


    public static List<QueryRecord> getUsersByExp(String guild_id, int limit) {
        if (limit == 0) {
            return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC;");
        }
        return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC limit " + limit + ";");
    }



    public static boolean toggleLevelUp(String guild_id, boolean toggle) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, exp_enabled) VALUES ('" + guild_id + "', '" + (toggle ? "1" : "0") + "') ON DUPLICATE KEY UPDATE exp_enabled = '" + (toggle ? "1" : "0") + "';");
    }

    public static boolean toggleBlacklist(String guild_id, boolean toggle) {
        return instance.defaultQuery("UPDATE guild SET blacklist_enabled = " + (toggle ? 1 : 0) + " WHERE guild_id = '" + guild_id + "';");
    }


    public static boolean setPrefix(String guild_id, String prefix) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, prefix)" + "VALUES('" + guild_id + "','" + prefix +"') ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';");
    }

    public static boolean updatePrefix(String guild_id, String prefix) {
        return instance.defaultQuery("UPDATE guild SET prefix = '" + prefix + "' WHERE guild_id = '" + guild_id + "';");
    }

    public static boolean setGreet(String userId, String guildId, String soundId) {
        return com.safjnest.bot.cache.managers.UserCache.withUser(userId,
            user -> user.setGreet(guildId, soundId));
    }

    public static boolean deleteGreet(String userId, String guildId) {
        return com.safjnest.bot.cache.managers.UserCache.withUser(userId,
            user -> user.unsetGreet(guildId));
    }

    public static boolean setBlacklistChannel(String blacklist_channel, String guild_id) {
        return instance.defaultQuery("UPDATE guild SET blacklist_channel = '" + blacklist_channel + "' WHERE guild_id = '" + guild_id +  "';");
    }

    public static boolean setBlacklistThreshold(String threshold, String guild_id) {
        return instance.defaultQuery("UPDATE guild SET threshold = '" + threshold + "' WHERE guild_id = '" + guild_id +  "';");
    }

    public static boolean enableBlacklist(String guild_id, String threshold, String blacklist_channel) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, threshold, blacklist_channel, blacklist_enabled)" + "VALUES('" + guild_id + "','" + threshold +"', '" + blacklist_channel + "', 1) ON DUPLICATE KEY UPDATE threshold = '" + threshold + "', blacklist_channel = '" + blacklist_channel + "', blacklist_enabled = 1;");
    }

    public static boolean insertUserBlacklist(String user_id, String guild_id){
        return instance.defaultQuery("INSERT INTO blacklist VALUES('" + user_id + "', '" + guild_id + "')");
    }

    public static int getBlacklistBan(String user_id){
        return instance.lineQuery("SELECT count(user_id) as times from blacklist WHERE user_id = '" + user_id + "'").getAsInt("times");
    }

    public static boolean deleteBlacklist(String guild_id, String user_id){
        return instance.defaultQuery("DELETE FROM blacklist WHERE guild_id = '" + guild_id + "' AND user_id = '" + user_id + "'");
    }

    public static List<QueryRecord> getGuildByThreshold(int threshold, String guild_id){
        return instance.query("SELECT guild_id, blacklist_channel, threshold FROM guild WHERE blacklist_enabled = 1 AND threshold <= '" + threshold + "' AND blacklist_channel IS NOT NULL AND guild_id != '" + guild_id + "'");
    }

    public static boolean insertCommand(String guild_id, String author_id, String command, String args){
        return instance.defaultQuery("INSERT INTO command(name, time, user_id, guild_id, args) VALUES ('" + command + "', '" + new Timestamp(System.currentTimeMillis()) + "', '" + author_id + "', '"+ guild_id +"', '"+ fixSQL(args) +"');");
    }

    public static int getBannedTimes(String user_id){
        return instance.lineQuery("SELECT count(user_id) as times from blacklist WHERE user_id = '" + user_id + "'").getAsInt("times");
    }

    public static int getBannedTimesInGuild(String guild_id){
        return instance.lineQuery("SELECT count(user_id) as times from blacklist WHERE guild_id = '" + guild_id + "'").getAsInt("times");
    }

    @Deprecated
    public static QueryRecord getGreet(String user_id, String guild_id) {
        return instance.lineQuery("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND (greeting.guild_id = '" + guild_id + "' OR greeting.guild_id = '0') ORDER BY CASE WHEN greeting.guild_id = '0' THEN 1 ELSE 0 END LIMIT 1;");
    }

    public static QueryRecord getSpecificGuildGreet(String user_id, String guild_id) {
        return instance.lineQuery("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '" + guild_id + "' LIMIT 1;");
    }

    public static QueryRecord getGlobalGreet(String user_id) {
        return instance.lineQuery("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '0' LIMIT 1;");
    }


    public static boolean setAlertMessage(String ID, String message) {
        Connection c = instance.getConnection();
        if(c == null) return false;

        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
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

    public static boolean setAlertPrivateMessage(String ID, String message) {
        Connection c = instance.getConnection();
        if(c == null) return false;

        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET private_message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
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

    public static boolean setAlertChannel(String ID, String channel) {
        return instance.defaultQuery("UPDATE alert SET channel = '" + channel + "' WHERE ID = '" + ID + "';");
    }

    public static boolean setAlertEnabled(String ID, boolean toggle) {
        return instance.defaultQuery("UPDATE alert SET enabled = '" + (toggle ? 1 : 0) + "' WHERE ID = '" + ID + "';");
    }

    public static List<QueryRecord> getAlertsRoles(String guild_id) {
        return instance.query("SELECT r.id as row_id, a.id as alert_id, r.role_id as role_id  FROM alert_role as r JOIN alert as a ON r.alert_id = a.id WHERE a.guild_id = '" + guild_id + "';");
    }

    public static int createAlert(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, AlertType type) {
        int id = 0;
        String query = "INSERT INTO alert(guild_id, message, private_message, channel, enabled, send_type, type) VALUES(?, ?, ?, ?, 1, ?, ?);";

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (PreparedStatement pstmt = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guild_id);
            pstmt.setString(2, message);
            if (privateMessage != null) {
                pstmt.setString(3, privateMessage);
            } else {
                pstmt.setNull(3, Types.VARCHAR);
            }
            pstmt.setString(4, channelId);
            pstmt.setInt(5, sendType.ordinal());
            pstmt.setInt(6, type.ordinal());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                }
            }
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
        return id;
    }

    public static boolean createRewardData(String alertID, int level, boolean temporary) {
        return instance.defaultQuery("INSERT INTO alert_reward(alert_id, level, temporary) VALUES('" + alertID + "', '" + level + "', '" + (temporary ? 1 : 0) + "');");
    }

    public static boolean createTwitchData(String alertId, String streamerId, String roleId) {
        String query = "INSERT INTO alert_twitch (alert_id, streamer_id, role_id) VALUES (?, ?, ?);";
        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, alertId);
            pstmt.setString(2, streamerId);
            if (roleId != null) {
                pstmt.setString(3, roleId);
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }
            int affectedRows = pstmt.executeUpdate();
            conn.commit();
            return affectedRows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateTwitchRole(int alertId, String roleId) {
        return instance.defaultQuery("UPDATE alert_twitch SET role_id = '" + roleId + "' WHERE alert_id = '" + alertId + "';");
    }

    public static List<QueryRecord> getAlerts(String guild_id) {
        String query = "SELECT " +
                       "a.id AS alert_id, " +
                       "a.message, " +
                       "a.private_message, " +
                       "a.channel, " +
                       "a.enabled, " +
                       "a.send_type, " +
                       "a.type, " +
                       "r.id AS reward_id, " +
                       "r.level AS level, " +
                       "r.temporary AS temporary, " +
                       "t.id AS twitch_id, " +
                       "t.streamer_id, " +
                       "t.role_id " +
                       "FROM alert AS a " +
                       "LEFT JOIN alert_reward AS r ON a.id = r.alert_id " +
                       "LEFT JOIN alert_twitch AS t ON a.id = t.alert_id " +
                       "WHERE a.guild_id = '" + guild_id + "';";
        return instance.query(query);
    }

    public static boolean deleteAlert(String valueOf) {
        return instance.defaultQuery("DELETE FROM alert WHERE id = '" + valueOf + "';");
    }

    public static boolean deleteAlertRoles(String valueOf) {
        return instance.defaultQuery("DELETE FROM alert_role WHERE alert_id = '" + valueOf + "';");
    }

    public static boolean alertUpdateSendType(String valueOf, AlertSendType sendType) {
        return instance.defaultQuery("UPDATE alert SET send_type = '" + sendType.ordinal() + "' WHERE id = '" + valueOf + "';");
    }

    public static HashMap<Integer, String> createRolesAlert(String valueOf, String[] roles) {

        String values = "";
        for(String role : roles) {
            if(role != null) {
                values += "('" + valueOf + "', '" + role + "'), ";
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        values = values.substring(0, values.length() - 2);
        if (deleteAlertRoles(valueOf) && instance.defaultQuery("INSERT INTO alert_role(alert_id, role_id) VALUES " + values + ";")) {
            HashMap<Integer, String> roleMap = new HashMap<>();
            List<QueryRecord> result = instance.query("SELECT id, role_id FROM alert_role WHERE alert_id = '" + valueOf + "';");
            for(QueryRecord row : result) {
                roleMap.put(row.getAsInt("id"), row.get("role_id"));
            }
            return roleMap;
        }

        return null;

    }


    public static String insertChannelData(String guild_id, String channel_id) {
        String id = "0";

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO channel(guild_id, channel_id) VALUES('" + guild_id + "','" + channel_id + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
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
        return id;
    }

    public static List<QueryRecord> getChannelData(String guild_id) {
        return instance.query("SELECT id, channel_id, guild_id, exp_enabled, exp_modifier, stats_enabled, league_shard FROM channel WHERE guild_id = '" + guild_id + "';");
    }

    public static boolean setChannelExpModifier(String ID, double exp_modifier) {
        return instance.defaultQuery("UPDATE channel SET exp_modifier = '" + exp_modifier + "' WHERE id = '" + ID + "';");
    }

    public static boolean setChannelExpEnabled(String ID, boolean toggle) {
        return instance.defaultQuery("UPDATE channel SET exp_enabled = '" + (toggle ? 1 : 0) + "' WHERE id = '" + ID + "';");
    }

    public static boolean setChannelCommandEnabled(String ID, boolean toggle) {
        return instance.defaultQuery("UPDATE channel SET stats_enabled = '" + (toggle ? 1 : 0) + "' WHERE id = '" + ID + "';");
    }

    public static boolean deleteChannelData(String ID) {
        return instance.defaultQuery("DELETE FROM channel WHERE id = '" + ID + "';");
    }


    public static String insertUserData(String guild_id, String user_id) {
        String id = "0";

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO member(guild_id, user_id) VALUES('" + guild_id + "','" + user_id + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
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
        return id;
    }

    public static QueryRecord getUserData(String guild_id, String user_id) {
        return instance.lineQuery("SELECT id, user_id, guild_id, experience, level, messages, update_time FROM member WHERE user_id = '"+ user_id +"' AND guild_id = '" + guild_id + "';");
    }

    public static boolean updateUserDataExperience(String ID, int experience, int level, int messages) {
        return instance.defaultQuery("UPDATE member SET experience = '" + experience + "', level = '" + level + "', messages = '" + messages + "' WHERE id = '" + ID + "';");
    }

    public static boolean updateUserDataUpdateTime(String ID, int updateTime) {
        return instance.defaultQuery("UPDATE member SET update_time = '" + updateTime + "' WHERE id = '" + ID + "';");
    }

    public static QueryRecord getUserExp(String id, String id2) {
        return instance.lineQuery("SELECT experience, level, messages FROM member WHERE user_id = '" + id + "' AND guild_id = '" + id2 + "'");
    }


    public static List<QueryRecord> getAliases(String user_id) {
        return instance.query("SELECT id, name, command FROM alias WHERE user_id = '" + user_id + "';");
    }

    public static int createAlias(String user_id, String name, String command) {
        int id = 0;

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO alias(user_id, name, command) VALUES('" + user_id + "','" + name + "','" + command + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
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
        return id;
    }

    public static boolean deleteAlias(String toDelete) {
        return instance.defaultQuery("DELETE FROM alias WHERE id = '" + toDelete + "';");
    }



    public static HashMap<String, List<QueryRecord>> getCustomCommandData(String guild_id) {
        HashMap<String, List<QueryRecord>> commandData = new HashMap<>();
        List<QueryRecord> result = instance.query("SELECT ID,name,description,slash FROM commands WHERE guild_id = " + guild_id);

        if (result.isEmpty()) {
            return null;
        }
        commandData.put("commands", result);

        String ids = String.join(", ", columnValues(result, "ID"));

        List<QueryRecord> optionResult = instance.query("SELECT ID,command_id,`key`,description,required,type FROM command_option WHERE command_id IN (" + ids + ")");
        commandData.put("options", optionResult);

        List<QueryRecord> valueResult = null;
        if (!optionResult.isEmpty()) {
            String optionIds = String.join(", ", columnValues(optionResult, "ID"));
            valueResult = instance.query("SELECT ID,option_id,`key`,value FROM command_option_value WHERE option_id IN (" + optionIds + ")");
            commandData.put("values", valueResult);
        }

        List<QueryRecord> taskResult = instance.query("SELECT ID,command_id,type,`order` FROM command_task WHERE command_id IN (" + ids + ") order by `order`");
        commandData.put("tasks", taskResult);

        String taskIds = String.join(", ", columnValues(taskResult, "ID"));
        List<QueryRecord> taskValueResult = instance.query("SELECT ID,task_id,value,from_option FROM command_task_value WHERE task_id IN (" + taskIds + ")");
        commandData.put("task_values", taskValueResult);

        String taskValueIds = String.join(", ", columnValues(taskValueResult, "ID"));
        List<QueryRecord> taskMessage = instance.query("SELECT ID,task_value_id,message FROM command_task_message WHERE task_value_id IN (" + taskValueIds + ")");
        if (!taskMessage.isEmpty()) {
            commandData.put("task_messages", taskMessage);
        }
        return commandData;
    }

    public static boolean updateShard(String valueOf, LeagueShard shard) {
        return instance.defaultQuery("UPDATE guild SET league_shard = '" + shard.ordinal() + "' WHERE guild_id = '" + valueOf + "';");
    }

    public static boolean updateShardChannel(String valueOf, LeagueShard shard) {
        return instance.defaultQuery("UPDATE channel SET league_shard = '" + shard.ordinal() + "' WHERE id = '" + valueOf + "';");
    }

    public static List<QueryRecord> getTwitchSubscriptions(String streamer_id) {
        try (SoundSession session = instance.connectSound()) {
            return instance.soundQuery(session.connection(), """
                SELECT a.guild_id FROM alert_twitch t JOIN alert a ON a.id = t.alert_id WHERE t.streamer_id = ?
                """, streamer_id);
        } catch (SQLException exception) {
            throw new IllegalStateException("Twitch alert storage is unavailable", exception);
        }
    }

    public static List<QueryRecord> getSoundTags(String sound_id) {
        return instance.query("SELECT ts.tag_id as id,t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id = '" + sound_id + "';");
    }

    public static List<QueryRecord> getSoundsTags(String ...sound_id) {
        StringBuilder sb = new StringBuilder();
        for(String sound : sound_id) {
            sb.append("'" + sound + "', ");
        }
        sb.setLength(sb.length() - 2);
        return instance.query("SELECT ts.sound_id as sound_id, ts.tag_id as tag_id, t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id IN (" + sb.toString() + ");");
    }

    public static QueryRecord getTag(String id) {
        List<QueryRecord> rows = instance.readSoundRows("SELECT id, name FROM tag WHERE id = ?", id);

        return rows.isEmpty() ? new QueryRecord() : rows.getFirst();
    }



    public static int createPlaylist(String name, String user_id) {
        int id = 0;

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO playlist(name, user_id) VALUES('" + name + "','" + user_id + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
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
        return id;
    }

    public static List<QueryRecord> getPlaylists(String user_id) {
        return instance.query("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "';");
    }

    public static QueryRecord getPlaylist(String user_id, int playlist_id) {
        return instance.lineQuery("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "' AND id = " + playlist_id + ";");
    }

    public static List<QueryRecord> getPlaylistsWithSize(String user_id) {
        return instance.query("SELECT id, name, created_at, (select count(*) as size from playlist_track where playlist_id = p.id) as size FROM playlist p WHERE user_id = '" + user_id + "';");
    }

    public static QueryRecord getPlaylistByIdWithSize(int playlist_id) {
        return instance.lineQuery("SELECT id, name, user_id, created_at, (select count(*) as size from playlist_track where playlist_id = p.id) as size FROM playlist p WHERE id = '" + playlist_id + "';");
    }

    public static int deletePlaylist(int playlist_id, String user_id) {

        Connection c = instance.getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = instance.lineQuery("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            instance.query(stmt, "DELETE FROM playlist WHERE id = '" + playlist_id + "';");
            c.commit();

            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
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

    public static int deletePlaylistSong(int playlist_id, int song_id, String user_id) {
        Connection c = instance.getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = instance.lineQuery("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            instance.query(stmt, "DELETE FROM playlist_track WHERE id = '" + song_id + "';");
            c.commit();
            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
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

    public static int updatePlaylistOrder(int playlist_id, String user_id, List<String> song_ids) {
        Connection c = instance.getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = instance.lineQuery("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            int order = 0;
            for(String song_id : song_ids) {
                instance.query(stmt, "UPDATE playlist_track SET `order` = '" + order + "' WHERE id = '" + song_id + "';");
                order++;
            }
            c.commit();
            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
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


    public static boolean playlistExixtes(String name, String user_id) {
        return !instance.query("SELECT 1 FROM playlist WHERE name = '" + name + "' AND user_id = '" + user_id + "'").isEmpty();
    }

    public static List<QueryRecord> getPlaylistTracks(int playlist_id, Integer limit, Integer page) {
        String limitString = limit != null ? " LIMIT " + limit + " " : "";
        limitString += page != null ? " OFFSET " + (page * limit) + " " : "";
        return instance.query("SELECT * FROM playlist_track WHERE playlist_id = " + playlist_id + " ORDER BY `order` ASC" + limitString);
    }

    public static boolean addTrackToPlaylist(int playlist_id, String uri, String encoded_track, Integer order) {
        if(order == null) {
            return instance.defaultQuery("SET @max_order = (SELECT COALESCE(MAX(`order`), 0) FROM playlist_track WHERE playlist_id = " + playlist_id + ")", "INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES (" + playlist_id + ", '" + uri + "', '" + encoded_track + "', @max_order + 1);");
        } else {
            return instance.defaultQuery("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES (" + playlist_id + ", '" + uri + "', '" + encoded_track + "', " + order + ")");
        }
    }

    public static boolean addTrackToPlaylist(int playlist_id, List<AudioTrack> tracks, Integer order) {
        StringBuilder queryBuilder = new StringBuilder("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES ");

        Connection c = instance.getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            int currentOrder = order != null ? order : instance.lineQuery(stmt, "SELECT COALESCE(MAX(`order`), 0) AS max_order FROM playlist_track WHERE playlist_id = " + playlist_id).getAsInt("max_order");
            for (AudioTrack track : tracks) {
                queryBuilder.append("(")
                    .append(playlist_id).append(", '")
                    .append(track.getInfo().uri).append("', '")
                    .append(PlayerManager.get().encodeTrack(track)).append("', ")
                    .append(currentOrder++).append("),");
            }
            queryBuilder.setLength(queryBuilder.length() - 1);
            queryBuilder.append(";");
            instance.query(stmt, queryBuilder.toString());

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

    public static boolean addTrackToPlaylist(String playlist_name, String user_id, List<AudioTrack> tracks, Integer order) {
        StringBuilder queryBuilder = new StringBuilder("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES ");

        Connection c = instance.getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            int playlist_id = instance.lineQuery("SELECT id FROM playlist WHERE name = '" + playlist_name + "' AND user_id = '" + user_id + "'").getAsInt("id");
            int currentOrder = order != null ? order : instance.lineQuery(stmt, "SELECT COALESCE(MAX(`order`), 0) AS max_order FROM playlist_track WHERE playlist_id = " + playlist_id).getAsInt("max_order");
            for (AudioTrack track : tracks) {
                queryBuilder.append("(")
                    .append(playlist_id).append(", '")
                    .append(track.getInfo().uri).append("', '")
                    .append(PlayerManager.get().encodeTrack(track)).append("', ")
                    .append(currentOrder++).append("),");
            }
            queryBuilder.setLength(queryBuilder.length() - 1);
            queryBuilder.append(";");
            instance.query(stmt, queryBuilder.toString());

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

    public static int createAutomatedAction(String guildId, int action, String role, Integer actionTime, int infractions, Integer infractionsTime) {
        String query = "INSERT INTO automated_action (guild_id, action, action_role, action_time, infractions, infractions_time) VALUES(?, ?, ?, ?, ?, ?);";
        int id = 0;
        Connection c = instance.getConnection();
        try (PreparedStatement pstmt = c.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, action);

            if (role != null) {
                pstmt.setString(3, role);
            } else {
                pstmt.setNull(3, Types.VARCHAR);
            }

            if (actionTime != null) {
                pstmt.setInt(4, actionTime);
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }

            pstmt.setInt(5, infractions);

            if (infractionsTime != null) {
                pstmt.setInt(6, infractionsTime);
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }

            pstmt.execute();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                }
            }
            c.commit();
            return id;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -1;
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

    public static int insertWarn(String memberId, String description) {
        Connection c = instance.getConnection();
        if(c == null) return -1;

        int id = -1;
        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO warning (member_id, reason) VALUES('" + memberId + "','" + description + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
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
        return id;
    }

    public static int getMemberWarnings(String memberId, int infractionsTime) {
        return instance.lineQuery("SELECT COUNT(*) AS warning_count FROM warning WHERE member_id = " + memberId + " AND `time` >= NOW() - INTERVAL " + infractionsTime + " SECOND " +  "GROUP BY member_id").getAsInt("warning_count");
    }

    public static int getMemberWarnings(String memberId) {
        return instance.lineQuery("SELECT COUNT(*) AS warning_count FROM warning WHERE member_id = " + memberId + " GROUP BY member_id").getAsInt("warning_count");
    }

    public static List<QueryRecord> getWarnings(String valueOf) {
        return instance.query("SELECT id, action, action_role, action_time, infractions, infractions_time FROM automated_action WHERE guild_id = '" + valueOf + "' ORDER BY infractions DESC");
    }

    public static List<QueryRecord> getAutomatedActionsExpiring() {
        String query = "SELECT aae.*, u.user_id, u.guild_id " + "FROM automated_action_expiration aae " + "JOIN `member` u ON aae.member_id = u.id " + "WHERE aae.time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 2 HOUR)";
        return instance.query(query);
    }

    public static String insertAutomatedActionExpiring(String member_id, int action, long time) {
        Connection c = instance.getConnection();
        if(c == null) return "-1";;

        String id = "-1";
        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO automated_action_expiration (member_id, action_id, time) VALUES('" + member_id + "','" + action + "','" + new Timestamp(time) + "');");
            id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
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
        return id;
    }

    public static String getMutedRole(String valueOf) {
        return instance.lineQuery("SELECT action_role FROM automated_action WHERE action = 1 AND guild_id = '" + valueOf + "';").get("action_role");
    }




    /**
     * @deprecated
     * deprecated this shit and use querySafe
     * @param s
     * @return
     */
    public static String fixSQL(String s){
        s = s.replace("\"", "\\\"");
        s = s.replace("\'", "\\\'");
        return s;
    }

    public static String normalize(String string) {
        String[] parts = string.split(",");

        List<Integer> list = new ArrayList<>();
        for (String part : parts) {
            list.add(Integer.parseInt(part.trim()));
        }

        Collections.sort(list);

        StringBuilder sortedString = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sortedString.append(list.get(i));
            if (i < list.size() - 1) {
                sortedString.append(",");
            }
        }

        return sortedString.toString();
    }

    public static List<QueryRecord> test() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("name", "eee");
        values.put("guild_id", "608967318789160970");
        values.put("user_id", "383358222972616705");
        values.put("extension", "mp3");
        return instance.insert("sound", values);
    }

    public List<AlertData> readGuildAlerts(String guildId) {
        return guildTransaction(true, connection -> readGuildAlerts(connection, guildId));
    }

    public record SavedAlert(int id, Map<Integer, String> roles) {}

    public void saveGeneral(GuildData edit) {
        guildTransaction(false, connection -> {
            lockGuild(connection, edit.getId(), edit.getPrefix());

            guildExecute(connection,
                "UPDATE guild SET prefix = ?, exp_enabled = ?, name_tts = ?, language_tts = ?, league_shard = ? WHERE guild_id = ?",
                edit.getPrefix(), edit.isExperienceEnabled(), edit.getVoice(), edit.getLanguage(),
                edit.getLeagueShard().ordinal(), edit.getId());

            return null;
        });
    }

    public void saveBlacklist(String guildId, String defaultPrefix, BlacklistData edit) {
        guildTransaction(false, connection -> {
            lockGuild(connection, guildId, defaultPrefix);

            guildExecute(connection,
                "UPDATE guild SET blacklist_enabled = ?, threshold = ?, blacklist_channel = ? WHERE guild_id = ?",
                edit.isBlacklistEnabled(), edit.getThreshold(), edit.getBlackChannelId(), guildId);

            return null;
        });
    }

    public int saveChannel(String guildId, String defaultPrefix, ChannelData edit) {
        return guildTransaction(false, connection -> {
            lockGuild(connection, guildId, defaultPrefix);

            List<QueryRecord> rows = guildQuery(connection,
                "SELECT id FROM channel WHERE guild_id = ? AND channel_id = ?", guildId, edit.getRoomId());
            int id;

            if (rows.isEmpty()) {
                id = guildInsert(connection,
                    "INSERT INTO channel (guild_id, channel_id, exp_enabled, exp_modifier, stats_enabled, league_shard) VALUES (?, ?, ?, ?, ?, ?)",
                    guildId, edit.getRoomId(), edit.isExpSystemEnabled(), edit.getExperienceModifier(),
                    edit.getCommand(), edit.getLeagueShard() == null ? 0 : edit.getLeagueShard().ordinal());
            } else {
                id = rows.get(0).getAsInt("id");

                guildExecute(connection,
                    "UPDATE channel SET exp_enabled = ?, exp_modifier = ?, stats_enabled = ?, league_shard = ? WHERE id = ?",
                    edit.isExpSystemEnabled(), edit.getExperienceModifier(), edit.getCommand(),
                    edit.getLeagueShard() == null ? 0 : edit.getLeagueShard().ordinal(), id);
            }

            return id;
        });
    }

    public SavedAlert saveAlert(AlertData edit) {
        return guildTransaction(false, connection -> {
            lockGuild(connection, edit.getGuildId(), edit.getDefaultPrefix());

            int id = edit.getID();

            if (id == 0) {
                id = guildInsert(connection,
                    "INSERT INTO alert (guild_id, message, private_message, channel, enabled, send_type, type) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    edit.getGuildId(), edit.getMessage(), edit.getPrivateMessage(), edit.getChannelId(),
                    edit.isEnabled(), edit.getSendType().ordinal(), edit.getType().ordinal());
            } else {
                if (guildQuery(connection, "SELECT id FROM alert WHERE id = ? AND guild_id = ? FOR UPDATE", id, edit.getGuildId()).isEmpty())
                    throw new IllegalStateException("Alert no longer exists");

                guildExecute(connection,
                    "UPDATE alert SET message = ?, private_message = ?, channel = ?, enabled = ?, send_type = ? WHERE id = ? AND guild_id = ?",
                    edit.getMessage(), edit.getPrivateMessage(), edit.getChannelId(), edit.isEnabled(),
                    edit.getSendType().ordinal(), id, edit.getGuildId());
            }

            if (edit instanceof TwitchData twitch) {
                if (edit.getID() == 0)
                    guildExecute(connection,
                        "INSERT INTO alert_twitch (alert_id, streamer_id, role_id) VALUES (?, ?, ?)",
                        id, twitch.getStreamer(), twitch.getStreamerRole());
                else
                    guildExecute(connection,
                        "UPDATE alert_twitch SET role_id = ? WHERE alert_id = ?",
                        twitch.getStreamerRole(), id);
            }

            if (edit instanceof RewardData reward) {
                if (!guildQuery(connection,
                        "SELECT a.id FROM alert a JOIN alert_reward r ON r.alert_id = a.id WHERE a.guild_id = ? AND r.level = ? AND a.id <> ?",
                        edit.getGuildId(), reward.getLevel(), id).isEmpty())
                    throw new IllegalStateException("A reward already exists for this level");

                if (edit.getID() == 0)
                    guildExecute(connection,
                        "INSERT INTO alert_reward (alert_id, level, temporary) VALUES (?, ?, ?)",
                        id, reward.getLevel(), reward.isTemporary());
                else
                    guildExecute(connection,
                        "UPDATE alert_reward SET level = ?, temporary = ? WHERE alert_id = ?",
                        reward.getLevel(), reward.isTemporary(), id);
            }

            guildExecute(connection, "DELETE FROM alert_role WHERE alert_id = ?", id);

            Map<Integer, String> roles = new LinkedHashMap<>();

            for (String role : edit.getRolesAsArray()) {
                int roleId = guildInsert(connection, "INSERT INTO alert_role (alert_id, role_id) VALUES (?, ?)", id, role);
                roles.put(roleId, role);
            }

            return new SavedAlert(id, roles);
        });
    }

    public void ensureGuild(String guildId, String defaultPrefix) {
        guildTransaction(false, connection -> {
            lockGuild(connection, guildId, defaultPrefix);

            return null;
        });
    }

    public boolean deleteGuildAlert(String guildId, int alertId, String defaultPrefix) {
        return guildTransaction(false, connection -> {
            lockGuild(connection, guildId, defaultPrefix);

            if (guildQuery(connection, "SELECT id FROM alert WHERE id = ? AND guild_id = ?", alertId, guildId).isEmpty())
                return false;

            guildExecute(connection, "DELETE FROM alert_role WHERE alert_id = ?", alertId);
            guildExecute(connection, "DELETE FROM alert_twitch WHERE alert_id = ?", alertId);
            guildExecute(connection, "DELETE FROM alert_reward WHERE alert_id = ?", alertId);

            return guildExecute(connection, "DELETE FROM alert WHERE id = ? AND guild_id = ?", alertId, guildId) == 1;
        });
    }

    public boolean deleteGuildChannel(String guildId, String channelId, String defaultPrefix) {
        return guildTransaction(false, connection -> {
            lockGuild(connection, guildId, defaultPrefix);

            return guildExecute(connection, "DELETE FROM channel WHERE guild_id = ? AND channel_id = ?", guildId, channelId) != 0;
        });
    }

    static List<QueryRecord> cacheRows(Connection connection, String query, Collection<String> ids) throws SQLException {
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(ids));
        List<QueryRecord> result = new ArrayList<>();

        for (int offset = 0; offset < keys.size(); offset += CACHE_BATCH_SIZE) {
            List<String> batch = keys.subList(offset, Math.min(keys.size(), offset + CACHE_BATCH_SIZE));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));

            try (PreparedStatement statement = connection.prepareStatement(String.format(query, placeholders))) {
                for (int index = 0; index < batch.size(); index++)
                    statement.setString(index + 1, batch.get(index));

                try (ResultSet rows = statement.executeQuery()) {
                    result.addAll(QueryRecordParser.fromRows(rows));
                }
            }
        }

        return result;
    }

    public record SoundPlay(String id, String soundId, String userId, String guildId, String channelId, Instant time, int source) {
        public SoundPlay(String id, String soundId, String userId, String guildId, Instant time, int source) {
            this(id, soundId, userId, guildId, null, time, source);
        }
    }

    public record SoundSelection(String userId, Set<String> guilds, String guildId, boolean mine,
            String q, String tag, String sort, String order, int limit) {
    }

    public List<String> selectSounds(SoundSelection selection) {
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
            where = soundVisibility(selection.userId(), selection.guilds(), args);
        }

        if (!selection.q().isEmpty()) {
            where += " AND LOWER(s.name) LIKE ? ESCAPE '!'";
            args.add("%" + escapeSoundQuery(selection.q()) + "%");
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

        for (QueryRecord row : readSoundRows(sql, args.toArray()))
            result.add(row.get("id"));

        return result;
    }

    public List<QueryRecord> readSounds(Collection<String> ids) {
        List<QueryRecord> result = new ArrayList<>();

        soundBatches(ids, batch -> result.addAll(readSoundRows(
            "SELECT * FROM sound WHERE id IN (" + soundMarks(batch.size()) + ")", batch.toArray())));

        return result;
    }

    public List<QueryRecord> readSoundTags(Collection<String> ids) {
        List<QueryRecord> result = new ArrayList<>();

        soundBatches(ids, batch -> result.addAll(readSoundRows(
            "SELECT DISTINCT ts.sound_id, t.id AS tag_id, t.name FROM tag_sounds ts JOIN tag t ON t.id = ts.tag_id WHERE ts.sound_id IN ("
                + soundMarks(batch.size()) + ") ORDER BY t.name, t.id", batch.toArray())));

        return result;
    }

    public List<QueryRecord> searchSoundTags(String q, String userId, Set<String> guilds) {
        List<Object> args = new ArrayList<>();
        args.add(escapeSoundQuery(q) + "%");
        String visible = soundVisibility(userId, guilds, args);

        return readSoundRows("SELECT DISTINCT t.id, t.name FROM tag t JOIN tag_sounds ts ON ts.tag_id=t.id JOIN sound s ON s.id=ts.sound_id WHERE LOWER(t.name) LIKE ? ESCAPE '!' AND " + visible + " ORDER BY t.name, t.id LIMIT 100", args.toArray());
    }

    public List<QueryRecord> readSoundEnrichment(Collection<String> ids, String userId) {
        List<QueryRecord> result = new ArrayList<>();

        soundBatches(ids, batch -> {
            List<Object> args = new ArrayList<>();
            args.add(userId);
            args.add(userId);
            args.addAll(batch);

            result.addAll(readSoundRows("SELECT s.id, (SELECT COUNT(DISTINCT h.user_id) FROM sound_play h WHERE h.sound_id=s.id) AS listeners, "
            + "(SELECT COUNT(*) FROM sound_play h WHERE h.sound_id=s.id AND h.user_id=?) AS my_plays, "
            + "(SELECT value FROM sound_vote i WHERE i.sound_id=s.id AND i.user_id=?) AS vote, "
            + "(SELECT COUNT(DISTINCT bs.id) FROM soundboard_sounds bs WHERE bs.sound_id=s.id) AS boards "
            + "FROM sound s WHERE s.id IN (" + soundMarks(batch.size()) + ")", args.toArray()));
        });

        return result;
    }

    public <T> T withSoundReadSession(Supplier<T> work) {
        if (soundReadSession.get() != null)
            return work.get();

        try (SoundSession session = connectSound()) {
            session.connection().setAutoCommit(false);
            session.connection().setReadOnly(true);
            session.connection().setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            soundReadSession.set(session);

            try {
                return work.get();
            } finally {
                soundReadSession.remove();
                session.connection().rollback();
            }
        } catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    public void forEachSoundPlay(Collection<String> ids, String guildId, Instant from, Instant to,
            java.util.function.Consumer<SoundPlay> consumer) {
        if (ids.isEmpty())
            return;

        withSoundReadSession(() -> {
            Connection connection = soundReadSession.get().connection();
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            long started = System.nanoTime();
            long rowsRead = 0;

            try {
                for (List<String> batch : soundPartition(ids)) {
                    List<Object> args = new ArrayList<>(batch);
                    String where = "sound_id IN (" + soundMarks(batch.size()) + ") AND time < ?";
                    args.add(Timestamp.from(to));

                    if (from != null) {
                        where += " AND time >= ?";
                        args.add(Timestamp.from(from));
                    }

                    if (guildId != null) {
                        where += " AND guild_id = ?";
                        args.add(guildId);
                    }

                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, sound_id, user_id, guild_id, channel_id, time, source FROM sound_play WHERE " + where,
                            ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                        statement.setFetchSize(SOUND_HISTORY_FETCH_SIZE);
                        bindSound(statement, args.toArray());

                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) {
                                consumer.accept(new SoundPlay(rows.getString("id"), rows.getString("sound_id"), rows.getString("user_id"),
                                    rows.getString("guild_id"), rows.getString("channel_id"), rows.getTimestamp("time", utc).toInstant(), rows.getInt("source")));
                                rowsRead++;
                            }
                        }
                    }
                }

                return null;
            } catch (SQLException exception) {
                throw soundUnavailable(exception);
            } finally {
                SOUND_LOG.debug("Sound history: {} rows processed in {} ms", rowsRead, (System.nanoTime() - started) / 1_000_000);
            }
        });
    }

    public List<QueryRecord> readGreets(String userId) {
        return readSoundRows("SELECT guild_id, sound_id FROM greeting WHERE user_id=?", userId);
    }

    public QueryRecord readSoundUserPlays(String soundId, String userId) {
        List<QueryRecord> rows = readSoundRows(
            "SELECT COUNT(*) AS times FROM sound_play WHERE sound_id = ? AND user_id = ?", soundId, userId);

        return rows.isEmpty() ? new QueryRecord() : rows.get(0);
    }

    public QueryRecord readSoundInteraction(String soundId, String userId) {
        List<QueryRecord> rows = readSoundRows(
            "SELECT value FROM sound_vote WHERE sound_id = ? AND user_id = ?", soundId, userId);

        return rows.isEmpty() ? new QueryRecord() : rows.get(0);
    }

    public List<Tag> saveSound(Sound edit, String userId, Consumer<Sound> synchronize) {
        List<Tag> saved = new ArrayList<>();

        soundTransaction(connection -> {
            QueryRecord current = lockSound(connection, edit.getId());
            if (!userId.equals(current.get("user_id")))
                throw soundForbidden();

            if (!edit.getName().equals(current.get("name")) && !soundQuery(connection,
                    "SELECT id FROM sound WHERE id <> ? AND name = ? AND (guild_id = ? OR user_id = ?)",
                    edit.getId(), edit.getName(), current.get("guild_id"), userId).isEmpty())
                throw new Exception(Type.INVALID_REQUEST, "duplicate_sound_name", "Sound name is already in use");

            soundExecute(connection, "UPDATE sound SET name = ?, description = ?, public = ? WHERE id = ?",
                edit.getName(), edit.getDescription(), edit.isPublic(), edit.getId());
            soundExecute(connection, "DELETE FROM tag_sounds WHERE sound_id = ?", edit.getId());

            List<String> names = new ArrayList<>();
            for (Tag tag : edit.getTags()) {
                if (!tag.isEmpty())
                    names.add(tag.getName());
            }

            saved.addAll(saveSoundTags(connection, edit.getId(), names));
            current.put("name", edit.getName());
            current.put("description", edit.getDescription());
            current.put("public", edit.isPublic());
            synchronize.accept(new Sound(current, saved));
        });

        return saved;
    }

    public Sound.Vote voteSound(String id, String userId, Set<String> guilds, int value, boolean enabled) {
        Sound.Vote[] saved = new Sound.Vote[1];

        soundTransaction(connection -> {
            QueryRecord sound = lockSound(connection, id);

            if (!isSoundVisible(sound, userId, guilds))
                throw soundMissing();

            saved[0] = SoundDB.setSoundVote(this, connection, sound, userId, value, enabled);
        });

        return saved[0];
    }

    public QueryRecord deleteSoundData(String id, String userId, Set<String> administrators) {
        QueryRecord[] removed = new QueryRecord[1];

        soundTransaction(connection -> {
            QueryRecord row = lockSound(connection, id);
            if (!userId.equals(row.get("user_id")) && !administrators.contains(row.get("guild_id")))
                throw soundForbidden();
            soundExecute(connection, "DELETE FROM sound WHERE id=?", id);
            removed[0] = row;
        });

        return removed[0];
    }

    public void saveGreet(String userId, String guildId, String soundId) {
        soundTransaction(connection -> {
            if (soundId == null) {
                soundExecute(connection, "DELETE FROM greeting WHERE user_id=? AND guild_id=?", userId, guildId);
                return;
            }

            QueryRecord sound = lockSound(connection, soundId);
            boolean allowed = guildId.equals("0") ? sound.getAsBoolean("public") || userId.equals(sound.get("user_id"))
                : sound.getAsBoolean("public") || guildId.equals(sound.get("guild_id"));

            if (!allowed)
                throw new Exception(Type.INVALID_REQUEST, "private_sound_wrong_guild", "Private sound cannot be used in this context");

            soundExecute(connection, "INSERT INTO greeting(user_id,guild_id,sound_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE sound_id=VALUES(sound_id)", userId, guildId, soundId);
        });
    }

    public int recordSoundPlay(String id, String userId, String guildId, int source) {
        return recordSoundPlay(id, userId, guildId, null, source);
    }

    public int recordSoundPlay(String id, String userId, String guildId, String channelId, int source) {
        return writeSound(connection -> {
            QueryRecord sound = lockSound(connection, id);

            return insertSoundPlay(connection, sound, userId, guildId, channelId, source, null);
        });
    }

    int insertSoundPlay(Connection connection, QueryRecord sound, String userId, String guildId,
            String channelId, int source, String playbackId) throws SQLException {
        String id = sound.get("id");
        if (playbackId != null && !soundQuery(connection,
                "SELECT id FROM sound_play WHERE sound_id=? AND playback_id=?", id, playbackId).isEmpty())
            return sound.getAsInt("plays");

        soundExecute(connection, "INSERT INTO sound_play(user_id,sound_id,guild_id,channel_id,source,playback_id) VALUES (?,?,?,?,?,?)",
            userId, id, guildId, channelId, source, playbackId);
        soundExecute(connection, "UPDATE sound SET plays=plays+1 WHERE id=?", id);

        return sound.getAsInt("plays") + 1;
    }

    public static boolean isSoundVisible(QueryRecord sound, String userId, Set<String> guilds) {
        String owner = sound.get("user_id");
        String guild = sound.get("guild_id");

        return sound.getAsBoolean("public") || (userId != null && userId.equals(owner))
            || (guild != null && guilds.contains(guild));
    }

    public List<QueryRecord> readSoundRows(String sql, Object... args) {
        SoundSession existing = soundReadSession.get();

        if (existing != null) {
            try {
                return soundQuery(existing.connection(), sql, args);
            } catch (SQLException exception) {
                throw soundUnavailable(exception);
            }
        }

        try (SoundSession session = connectSound()) {
            Connection connection = session.connection();
            return soundQuery(connection, sql, args);
        }
        catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    /* -------------------------------------------------------------------------- */

    private <T> T guildTransaction(boolean readOnly, GuildWork<T> work) {
        try (Connection connection = getConnection()) {
            if (connection == null)
                throw new SQLException("No database connection");

            connection.setAutoCommit(false);
            connection.setReadOnly(readOnly);

            if (readOnly)
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            try {
                T value = work.run(connection);

                connection.commit();

                return value;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Guild settings storage is unavailable", exception);
        }
    }

    private void lockGuild(Connection connection, String guildId, String defaultPrefix) throws SQLException {
        guildExecute(connection, "INSERT INTO guild (guild_id, prefix) VALUES (?, ?) ON DUPLICATE KEY UPDATE guild_id = guild_id", guildId, defaultPrefix);
        guildQuery(connection, "SELECT guild_id FROM guild WHERE guild_id = ? FOR UPDATE", guildId);
    }

    private List<AlertData> readGuildAlerts(Connection connection, String guildId) throws SQLException {
        List<QueryRecord> rows = guildQuery(connection,
            "SELECT a.id AS alert_id, a.guild_id, a.message, a.private_message, a.channel, a.enabled, a.send_type, a.type, "
                + "r.level, r.temporary, t.streamer_id, t.role_id FROM alert a "
                + "LEFT JOIN alert_reward r ON r.alert_id = a.id LEFT JOIN alert_twitch t ON t.alert_id = a.id "
                + "WHERE a.guild_id = ? ORDER BY a.id", guildId);

        Map<Integer, HashMap<Integer, String>> roles = new HashMap<>();

        for (QueryRecord row : guildQuery(connection,
                "SELECT r.id, r.alert_id, r.role_id FROM alert_role r JOIN alert a ON a.id = r.alert_id WHERE a.guild_id = ? ORDER BY r.id", guildId)) {
            roles.computeIfAbsent(row.getAsInt("alert_id"), ignored -> new LinkedHashMap<>())
                .put(row.getAsInt("id"), row.get("role_id"));
        }

        List<AlertData> alerts = new ArrayList<>();

        for (QueryRecord row : rows) {
            HashMap<Integer, String> alertRoles = roles.get(row.getAsInt("alert_id"));
            alerts.add(switch (AlertType.getFromOrdinal(row.getAsInt("type"))) {
                case REWARD -> new RewardData(row, alertRoles, this);
                case TWITCH -> new TwitchData(row, this);
                default -> new AlertData(row, alertRoles, this);
            });
        }

        return alerts;
    }

    private int guildInsert(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindGuild(statement, parameters);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next())
                    throw new SQLException("Missing generated ID");

                return keys.getInt(1);
            }
        }
    }

    private int guildExecute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindGuild(statement, parameters);

            return statement.executeUpdate();
        }
    }

    private List<QueryRecord> guildQuery(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindGuild(statement, parameters);

            try (ResultSet rows = statement.executeQuery()) {
                return QueryRecordParser.fromRows(rows);
            }
        }
    }

    private void bindGuild(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++)
            statement.setObject(index + 1, parameters[index]);
    }

    @FunctionalInterface
    private interface GuildWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private List<QueryRecord> cacheRows(String query, Collection<String> ids) {
        if (ids.isEmpty())
            return List.of();

        try (Connection connection = getConnection()) {
            if (connection == null)
                throw new SQLException("Connection to the database failed");

            try {
                List<QueryRecord> rows = cacheRows(connection, query, ids);

                connection.commit();

                return rows;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                }
                catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load cache data", exception);
        }
    }

    private static List<String> columnValues(List<QueryRecord> rows, String column) {
        List<String> values = new ArrayList<>();

        if (rows == null)
            return values;

        for (QueryRecord row : rows)
            values.add(row.get(column));

        return values;
    }


    private String soundVisibility(String user, Set<String> guilds, List<Object> args) {
        args.add(user);

        String where = "(s.public=1 OR s.user_id=?";

        if (!guilds.isEmpty()) {
            where += " OR s.guild_id IN (" + soundMarks(guilds.size()) + ")";
            args.addAll(guilds);
        }

        return where + ")";
    }

    private record SoundSession(Connection connection, String timeZone) implements AutoCloseable {
        public void close() throws SQLException {
            try {
                if (timeZone != null) {
                    try (PreparedStatement statement = connection.prepareStatement("SET time_zone = ?")) {
                        statement.setString(1, timeZone);
                        statement.execute();
                    }
                }
            } finally {
                connection.close();
            }
        }
    }

    private SoundSession connectSound() throws SQLException {
        Connection connection = getConnection();
        if (connection == null)
            throw new SQLException("No database connection");

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

            return new SoundSession(connection, originalZone);
        } catch (SQLException | RuntimeException exception) {
            connection.close();

            throw exception;
        }
    }

    private interface SoundWork {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface SoundWrite<T> {
        T run(Connection connection) throws SQLException;
    }

    private void soundTransaction(SoundWork work) {
        writeSound(connection -> {
            work.run(connection);

            return null;
        });
    }

    <T> T writeSound(SoundWrite<T> work) {
        try (SoundSession session = connectSound()) {
            Connection connection = session.connection();
            connection.setAutoCommit(false);

            try {
                T value = work.run(connection);
                connection.commit();

                return value;
            }
            catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }

                throw failure;
            }
        }
        catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    private String insertSoundboardRow(Connection connection, String name, String guildId, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO soundboard (name, guild_id, user_id) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bindSound(statement, new Object[] {name, guildId, userId});
            if (statement.executeUpdate() != 1)
                throw new SQLException("Soundboard insert did not create one row");

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next() || keys.getLong(1) <= 0)
                    throw new SQLException("Soundboard insert did not return an ID");

                return keys.getString(1);
            }
        }
    }

    private void changeSoundboard(String id, SoundboardAccess access, SoundWork change) {
        soundTransaction(connection -> {
            lockSoundboard(connection, id, access);
            change.run(connection);
        });
    }

    private QueryRecord lockSoundboard(Connection connection, String id, SoundboardAccess access) throws SQLException {
        List<QueryRecord> rows = soundQuery(connection,
            "SELECT name, user_id FROM soundboard WHERE guild_id = ? AND id = ? FOR UPDATE", access.guildId(), id);
        if (rows.isEmpty())
            throw soundboardMissing();

        QueryRecord current = rows.getFirst();
        if (!access.administrator() && !access.userId().equals(current.get("user_id")))
            throw new Exception(Type.FORBIDDEN, "soundboard_forbidden", "Only the uploader or a guild administrator may edit this soundboard");

        return current;
    }

    private void lockSoundboardGuild(Connection connection, String guildId) throws SQLException {
        if (soundQuery(connection, "SELECT guild_id FROM guild WHERE guild_id = ? FOR UPDATE", guildId).isEmpty())
            throw new Exception(Type.NOT_FOUND, "guild_not_found", "Guild is not stored");
    }

    private void requireUniqueSoundboardName(Connection connection, String name, String guildId, String exceptId) throws SQLException {
        List<QueryRecord> rows = soundQuery(connection, "SELECT id FROM soundboard WHERE guild_id = ? AND name = ?", guildId, name);
        for (QueryRecord row : rows) {
            if (!row.get("id").equals(exceptId))
                throw new Exception(Type.CONFLICT, "duplicate_soundboard_name", "Soundboard name is already in use");
        }
    }

    private void requireSoundboardSound(Connection connection, String soundId, String guildId) throws SQLException {
        QueryRecord sound = lockSound(connection, soundId);
        if (!sound.getAsBoolean("public") && !guildId.equals(sound.get("guild_id")))
            throw soundMissing();
    }

    private Exception soundboardMissing() {
        return new Exception(Type.NOT_FOUND, "soundboard_not_found", "Soundboard not found");
    }

    private boolean writeSoundboardThumbnail(String sql, Object... args) {
        try (SoundSession session = connectSound()) {
            Connection connection = session.connection();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindSound(statement, args);
                boolean changed = statement.executeUpdate() == 1;
                connection.commit();

                return changed;
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }

                throw failure;
            }
        } catch (SQLException exception) {
            throw soundUnavailable(exception);
        }
    }

    private List<String> soundIds(List<QueryRecord> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (QueryRecord row : rows)
            ids.add(row.get("id"));

        return ids;
    }

    private List<Tag> saveSoundTags(Connection connection, String soundId, List<String> names) throws SQLException {
        List<Tag> saved = new ArrayList<>();
        Set<Integer> ids = new LinkedHashSet<>();
        for (String name : new LinkedHashSet<>(names)) {
            List<QueryRecord> existing = soundQuery(connection,
                "SELECT id, name FROM tag WHERE name = ? ORDER BY id LIMIT 1", name);
            int tagId = existing.isEmpty()
                ? guildInsert(connection, "INSERT INTO tag(name) VALUES (?)", name)
                : existing.get(0).getAsInt("id");
            if (!ids.add(tagId))
                continue;

            soundExecute(connection, "INSERT INTO tag_sounds(sound_id, tag_id) VALUES (?, ?)", soundId, tagId);
            saved.add(new Tag(tagId, existing.isEmpty() ? name : existing.getFirst().get("name")));
        }

        return saved;
    }

    QueryRecord lockSound(Connection connection, String id) throws SQLException {
        List<QueryRecord> rows = soundQuery(connection, "SELECT * FROM sound WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty())
            throw soundMissing();

        return rows.get(0);
    }

    List<QueryRecord> soundQuery(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSound(statement, args);

            try (ResultSet result = statement.executeQuery()) {
                List<QueryRecord> rows = new ArrayList<>();

                while (result.next()) {
                    QueryRecord row = new QueryRecord();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++)
                        row.put(result.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),
                            result.getMetaData().getColumnType(i) == Types.TIMESTAMP
                                ? result.getTimestamp(i, Calendar.getInstance(TimeZone.getTimeZone("UTC"))) : result.getObject(i));

                    rows.add(row);
                }

                return rows;
            }
        }
    }

    void soundExecute(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSound(statement, args);
            statement.executeUpdate();
        }
    }

    void bindSound(PreparedStatement statement, Object[] args) throws SQLException {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Timestamp timestamp)
                statement.setTimestamp(i + 1, timestamp, utc);
            else
                statement.setObject(i + 1, args[i]);
        }
    }

    private List<List<String>> soundPartition(Collection<String> ids) {
        List<String> list = new ArrayList<>(new LinkedHashSet<>(ids));
        List<List<String>> soundBatches = new ArrayList<>();

        for (int i = 0; i < list.size(); i += SOUND_BATCH_SIZE)
            soundBatches.add(list.subList(i, Math.min(i + SOUND_BATCH_SIZE, list.size())));

        return soundBatches;
    }

    private void soundBatches(Collection<String> ids, java.util.function.Consumer<List<String>> consumer) {
        for (List<String> batch : soundPartition(ids))
            consumer.accept(batch);
    }

    private String soundMarks(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    static String escapeSoundQuery(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private Exception soundUnavailable(java.lang.Exception cause) {
        return new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Sound storage is unavailable", cause, null);
    }

    private Exception soundMissing() {
        return new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");
    }

    private Exception soundForbidden() {
        return new Exception(Type.FORBIDDEN, "sound_forbidden", "Sound operation is not permitted");
    }
}
