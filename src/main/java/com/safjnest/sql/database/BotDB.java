package com.safjnest.sql.database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.entities.Message.Attachment;

import com.safjnest.core.audio.PlayerManager;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.spring.entity.GuildEntity;
import com.safjnest.spring.entity.MemberEntity;
import com.safjnest.spring.service.GuildService;
import com.safjnest.spring.service.MemberService;
import com.safjnest.sql.AbstractDB;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

/**
 * Modernized BotDB that uses Spring Services underneath while maintaining compatibility
 * @deprecated Use Spring Services directly instead
 */
@Component
public class BotDB extends AbstractDB {
    private static BotDB instance;
    
    @Autowired
    private GuildService guildService;
    
    @Autowired
    private MemberService memberService;

    // Constructor injection for proper Spring initialization
    public BotDB() {
        // Let Spring handle the initialization
    }

    @Autowired
    public void setInstance(BotDB botDB) {
        instance = botDB;
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
    
    // Spring-backed implementations of key methods
    public static QueryRecord getGuildData(String guild_id) {
        if (instance.guildService != null) {
            GuildEntity guild = instance.guildService.getGuild(guild_id);
            if (guild == null) {
                return new QueryRecord(null);
            }
            
            QueryRecord record = new QueryRecord(null);
            record.put("guild_id", guild.getGuildId());
            record.put("prefix", guild.getPrefix());
            record.put("exp_enabled", guild.getExpEnabled() ? "1" : "0");
            record.put("threshold", String.valueOf(guild.getThreshold()));
            record.put("blacklist_channel", guild.getBlacklistChannel());
            record.put("blacklist_enabled", guild.getBlacklistEnabled() ? "1" : "0");
            record.put("name_tts", guild.getNameTts());
            record.put("language_tts", guild.getLanguageTts());
            record.put("league_shard", String.valueOf(guild.getLeagueShard()));
            
            return record;
        }
        
        // Fallback to old implementation
        String query = "SELECT guild_id, PREFIX, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id = '" + guild_id + "';";
        return instance.lineQuery(query);
    }

    public static boolean insertGuild(String guild_id, String prefix) {
        if (instance.guildService != null) {
            try {
                instance.guildService.getGuildOrCreate(guild_id);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        // Fallback to old implementation
        String query = "INSERT INTO guild (guild_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guild_id + "', '" + prefix + "', '1', '0', null) ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';";
        return instance.defaultQuery(query);
    }

    public static QueryResult getUsersByExp(String guild_id, int limit) {
        if (instance.memberService != null) {
            List<MemberEntity> members = instance.memberService.getUsersByExp(guild_id, limit);
            
            QueryResult result = new QueryResult();
            for (MemberEntity member : members) {
                QueryRecord record = new QueryRecord(null);
                record.put("user_id", member.getUserId());
                record.put("messages", String.valueOf(member.getMessages()));
                record.put("level", String.valueOf(member.getLevel()));
                record.put("experience", String.valueOf(member.getExperience()));
                result.add(record);
            }
            
            return result;
        }
        
        // Fallback to old implementation
        if (limit == 0) {
            return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC;");
        }
        return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC limit " + limit + ";");
    }

    public static boolean toggleLevelUp(String guild_id, boolean toggle) {
        if (instance.guildService != null) {
            return instance.guildService.updateExpEnabled(guild_id, toggle);
        }
        
        // Fallback to old implementation
        return instance.defaultQuery("INSERT INTO guild(guild_id, exp_enabled) VALUES ('" + guild_id + "', '" + (toggle ? "1" : "0") + "') ON DUPLICATE KEY UPDATE exp_enabled = '" + (toggle ? "1" : "0") + "';");
    }

    public static boolean updateVoiceGuild(String guild_id, String language, String voice) {
        if (instance.guildService != null) {
            return instance.guildService.updateVoiceSettings(guild_id, language, voice);
        }
        
        // Fallback to old implementation
        String query = "INSERT INTO guild (guild_id, language_tts, name_tts) VALUES ('" + guild_id + "', '" + language + "', '" + voice + "') ON DUPLICATE KEY UPDATE language_tts = '" + language + "', name_tts = '" + voice + "'";
        return instance.defaultQuery(query);
    }

    // Keep all the other existing methods for compatibility, but mark as deprecated

    public static QueryResult getGuildsData(String filter){
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild WHERE " + filter + ";";
        return instance.query(query);
    }

    public static List<Sound> getSounds(String user_id, int page, int limit) {
        QueryResult res = instance.query("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE user_id = '" + user_id + "' OR public = '1' ORDER BY id ASC LIMIT " + (page-1)*limit + ", " + limit);
        QueryResult tags = BotDB.getSoundsTags(res.arrayColumn("id").toArray(new String[0]));
        
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

    public static QueryResult getlistGuildSounds(String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryResult getlistGuildSounds(String guild_id, int limit) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT " + limit);
    }

    public static QueryResult getlistGuildSounds(String guild_id, String orderBy) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY " + orderBy +" ASC ");
    }

    public static QueryResult getGuildRandomSound(String guild_id){
        return instance.query("SELECT name, id FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryRecord getUserExp(String id, String id2) {
        return instance.lineQuery("SELECT experience, level, messages FROM member WHERE user_id = '" + id + "' AND guild_id = '" + id2 + "'");
    }

    // Member-related methods with Spring backing where possible
    public static QueryRecord getUserData(String guild_id, String user_id) {
        if (instance.memberService != null) {
            MemberEntity member = instance.memberService.getMember(guild_id, user_id);
            if (member == null) {
                return new QueryRecord(null);
            }
            
            QueryRecord record = new QueryRecord(null);
            record.put("id", String.valueOf(member.getId()));
            record.put("user_id", member.getUserId());
            record.put("guild_id", member.getGuildId());
            record.put("experience", String.valueOf(member.getExperience()));
            record.put("level", String.valueOf(member.getLevel()));
            record.put("messages", String.valueOf(member.getMessages()));
            record.put("update_time", String.valueOf(member.getUpdateTime()));
            
            return record;
        }
        
        // Fallback to old implementation
        return instance.lineQuery("SELECT id, user_id, guild_id, experience, level, messages, update_time FROM member WHERE user_id = '"+ user_id +"' AND guild_id = '" + guild_id + "';");
    }

    public static String insertUserData(String guild_id, String user_id) {
        if (instance.memberService != null) {
            MemberEntity member = instance.memberService.getMemberOrCreate(guild_id, user_id);
            return String.valueOf(member.getId());
        }
        
        // Fallback to old implementation
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

    public static boolean updateUserDataExperience(String ID, int experience, int level, int messages) {
        return instance.defaultQuery("UPDATE member SET experience = '" + experience + "', level = '" + level + "', messages = '" + messages + "' WHERE id = '" + ID + "';");
    }

    public static boolean updateUserDataUpdateTime(String ID, int updateTime) {
        return instance.defaultQuery("UPDATE member SET update_time = '" + updateTime + "' WHERE id = '" + ID + "';");
    }

    // Add more essential methods with @deprecated annotations to encourage migration to services
    @Deprecated
    public static boolean setPrefix(String guild_id, String prefix) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, prefix)" + "VALUES('" + guild_id + "','" + prefix +"') ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';");
    }

    @Deprecated 
    public static boolean updatePrefix(String guild_id, String prefix) {
        return instance.defaultQuery("UPDATE guild SET prefix = '" + prefix + "' WHERE guild_id = '" + guild_id + "';");
    }

    // Placeholder for all the other existing methods - they remain unchanged for now
    // This allows the application to continue working while we gradually migrate
    
    /**
     * @deprecated Use Spring Services instead
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

    // Add stubs for remaining methods to maintain compatibility
    public static QueryResult getSoundsTags(String ...sound_id) {
        StringBuilder sb = new StringBuilder();
        for(String sound : sound_id) {
            sb.append("'" + sound + "', ");
        }
        sb.setLength(sb.length() - 2);
        return instance.query("SELECT ts.sound_id as sound_id, ts.tag_id as tag_id, t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id IN (" + sb.toString() + ");");
    }

}

    public static QueryResult getGuildsData(String filter){
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild WHERE " + filter + ";";
        return instance.query(query);
    }

    public static List<Sound> getSounds(String user_id, int page, int limit) {
        QueryResult res = instance.query("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE user_id = '" + user_id + "' OR public = '1' ORDER BY id ASC LIMIT " + (page-1)*limit + ", " + limit);
        QueryResult tags = BotDB.getSoundsTags(res.arrayColumn("id").toArray(new String[0]));
        
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


    public static QueryResult getlistGuildSounds(String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryResult getlistGuildSounds(String guild_id, int limit) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT " + limit);
    }

    public static QueryResult getlistGuildSounds(String guild_id, String orderBy) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY " + orderBy +" ASC ");
    }



    public static QueryResult getGuildRandomSound(String guild_id){
        return instance.query("SELECT name, id FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getUserSound(String user_id){
        return instance.query("SELECT name, id, guild_id, extension FROM sound WHERE user_id = '" + user_id + "';");
    }

    public static QueryResult getlistUserSounds(String user_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY name ASC");
    }

    public static QueryResult getlistUserSoundsTime(String user_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY time ASC");
    }

    public static QueryResult getlistUserSounds(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY name ASC");
    }

    public static QueryResult getlistUserSoundsTime(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY time ASC");
    }

    public static QueryResult getFocusedGuildSound(String guild_id, String like){
        return instance.query("SELECT name, id FROM sound WHERE name LIKE '" + like + "%' AND guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedUserSound(String user_id, String like){
        return instance.query("SELECT name, id, guild_id FROM sound WHERE (name LIKE '" + like + "%' OR id LIKE '" + like + "%') AND user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getUserGuildSounds(String user_id, String guild_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' OR guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryResult getFocusedListUserSounds(String user_id, String guild_id, String like) {
        return instance.query("SELECT name, id, guild_id, extension FROM sound WHERE name LIKE '" + like + "%' OR id LIKE '" + like + "%' AND (user_id = '" + user_id + "' OR guild_id = '" + guild_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getSoundsById(String... sound_ids) {
        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids)
            sb.append(sound_id + ", ");
        sb.setLength(sb.length() - 2);

        return instance.query("SELECT id, name, guild_id, user_id, extension, public, time, plays, likes, dislikes FROM sound WHERE id IN (" + sb.toString() + ");");
    }

    public static QueryResult getSoundsById(String id, String guild_id, String author_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryRecord getSoundById(String id) {
        return instance.lineQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "'");
    }

    public static QueryResult getSoundsByName(String name, String guild_id, String author_id) {
        return instance.query("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryResult getDuplicateSoundsByName(String name, String guild_id, String author_id) {
        return instance.query("SELECT id, guild_id, user_id FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "' OR user_id = '" + author_id + "')");
    }

    public static QueryRecord getAuthorSoundById(String id, String user_id) {
        return instance.lineQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND user_id = '" + user_id + "'");
    }

    public static QueryRecord getAuthorSoundByName(String name, String user_id) {
        return instance.lineQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND user_id = '" + user_id + "'");
    }

    public static String insertSound(String name, String guild_id, String user_id, String extension, boolean isPublic) {
        Connection c = instance.getConnection();
        if(c == null) return null;

        String soundId = null;
        try (Statement stmt = c.createStatement()) {
            instance.query(stmt, "INSERT INTO sound(name, guild_id, user_id, extension, public, time) VALUES('" + name + "','" + guild_id + "','" + user_id + "','" + extension + "', " + ((isPublic == true) ? "1" : "0") + ", '" +  Timestamp.from(Instant.now()) + "'); ");
            soundId = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
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
        return soundId;
    }

    public static boolean updateSound(String id, String name, boolean isPublic) {
        return instance.defaultQuery("UPDATE sound SET name = '" + name + "', public = '" + (isPublic ? "1" : "0") + "' WHERE id = '" + id + "';");
    }

    public static boolean updateSound(String id, String name) {
        return instance.defaultQuery("UPDATE sound SET name = '" + name + "' WHERE id = '" + id + "';");
    }

    public static boolean updateSound(String id, boolean isPublic) {
        return instance.defaultQuery("UPDATE sound SET public = '" + (isPublic ? "1" : "0") + "' WHERE id = '" + id + "';");
    }

    public static boolean deleteSound(String id) {
        return instance.defaultQuery("DELETE FROM sound WHERE id = " + id + ";");
    }

    public static boolean updateUserPlays(String sound_id, String user_id, int source) {
        return instance.defaultQuery("INSERT INTO sound_history(user_id, sound_id, source) VALUES('" + user_id + "', '" + sound_id + "', " + source + ");", "UPDATE sound SET plays = plays + 1 WHERE id = '" + sound_id + "';");
    }

    public static QueryRecord getPlays(String sound_id, String user_id) {
        return instance.lineQuery("SELECT count(id) as times FROM sound_history WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "'");
    }

    public static QueryRecord getGlobalPlays(String sound_id) {
        return instance.lineQuery("SELECT count(id) as times FROM sound_history WHERE sound_id = '" + sound_id + "'");
    }

    public static String getSoundsUploadedByUserCount(String user_id) {
        return instance.lineQuery("select count(name) as count from sound where user_id = '" + user_id + "';").get("count");
    }

    public static String getSoundsUploadedByUserCount(String user_id, String guild_id) {
        return instance.lineQuery("select count(name) as count from sound where guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';").get("count");
    }

    public static String getTotalPlays(String user_id) {
        return instance.lineQuery("select count(id) as sum from sound_history where user_id = '" + user_id + "';").get("sum");
    }

    public static QueryRecord searchSoundboard(String string, String guild_id, String user_id) {
        return instance.lineQuery("SELECT id from soundboard WHERE (ID = '" + string + "' OR name = '" + string + "') AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')");
    }

    public static boolean soundboardExists(String id, String guild_id) {
        return !instance.lineQuery("SELECT id from soundboard WHERE name = '" + id + "' AND guild_id = '" + guild_id + "'").isEmpty();
    }

    public static boolean soundboardExists(String id, String guild_id, String user_id) {
        return !instance.lineQuery("SELECT id from soundboard WHERE ID = '" + id + "' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')").isEmpty();
    }

    public static int getSoundInSoundboardCount(String id) {
        return instance.lineQuery("SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'").getAsInt("count");
    }

    public static QueryResult getSoundsFromSoundBoard(String id) {
        return instance.query("select soundboard_sounds.sound_id as sound_id, sound.extension as extension, sound.name as name, sound.guild_id as guild_id from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + id + "' order by name");
    }

    public static QueryRecord getSoundboardByID(String id) {
        return instance.lineQuery("select name, thumbnail from soundboard where id = '" + id + "'");
    }

    public static QueryResult getRandomSoundboard(String guild_id, String user_id) {
        return instance.query("SELECT name, id, guild_id FROM soundboard WHERE guild_id = '" + guild_id + "' OR user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedSoundboard(String guild_id, String user_id, String like){
        return instance.query("SELECT name, id, guild_id FROM soundboard WHERE name LIKE '" + like + "%' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedSoundFromSounboard(String id, String like){
        return instance.query("SELECT s.name as name, s.id as sound_id, s.guild_id as guild_id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE s.name LIKE '" + like + "%' AND ss.id = '" + id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult extremeSoundResearch(String query) {
        return instance.query("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.name like '%" + query + "%' OR t.name like '%" + query + "%';");
        //return instance.query("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%';");
    }

    public static QueryResult extremeSoundResearch(String query, String user_id) {
        return instance.query("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.user_id = " + user_id + " AND (MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%');");
    }



    public static boolean insertSoundBoard(String name, Attachment attachment, String guild_id, String user_id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();

        for (String sound_id : sound_ids)
            sb.append("(LAST_INSERT_ID(), " + sound_id + "), ");
        sb.setLength(sb.length() - 2);

        Connection c = instance.getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            //instance.defaultQuery(stmt, "INSERT INTO soundboard (name, thumbnail, guild_id, user_id) VALUES ('" + name + "', '" + (attachment != null ? attachment.getUrl() : "") + "', '" + guild_id + "', '" + user_id + "'); ");

            String query = "INSERT INTO soundboard (name, thumbnail, guild_id, user_id) VALUES (?, ?, ?, ?);";
            try (PreparedStatement pstmt = c.prepareStatement(query)) {
                pstmt.setString(1, name);
                if (attachment != null) {
                    CompletableFuture<InputStream> futureInputStream = attachment.getProxy().download();
                    InputStream thumbnail = futureInputStream.join();
                    pstmt.setBlob(2, thumbnail);
                } else {
                    pstmt.setNull(2, java.sql.Types.BLOB);
                }
                pstmt.setString(3, guild_id);
                pstmt.setString(4, user_id);

                pstmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }



            System.out.println("INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + ";");
            instance.query(stmt, "INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + ";");
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

    public static boolean updateSoundboardThumbnail(String id, Attachment thumbnail) {
        Connection c = instance.getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            String query = "UPDATE soundboard SET thumbnail = ? WHERE id = ?;";
            try (PreparedStatement pstmt = c.prepareStatement(query)) {
                CompletableFuture<InputStream> futureInputStream = thumbnail.getProxy().download();
                InputStream thumbnailStream = futureInputStream.join();
                pstmt.setBlob(1, thumbnailStream);
                pstmt.setString(2, id);

                pstmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
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

    public static QueryResult getGuildData(){
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel, blacklist_enabled FROM guild;";
        return instance.query(query);
    }

    public static QueryRecord getGuildData(String guild_id) {
        String query = "SELECT guild_id, PREFIX, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id = '" + guild_id + "';";
        return instance.lineQuery(query);
    }

    public static boolean updateVoiceGuild(String guild_id, String language, String voice) {
        String query = "INSERT INTO guild (guild_id, language_tts, name_tts) VALUES ('" + guild_id + "', '" + language + "', '" + voice + "') ON DUPLICATE KEY UPDATE language_tts = '" + language + "', name_tts = '" + voice + "'";
        return instance.defaultQuery(query);
    }

    public static boolean insertGuild(String guild_id, String prefix) {
        String query = "INSERT INTO guild (guild_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guild_id + "', '" + prefix + "', '1', '0', null) ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';";
        return instance.defaultQuery(query);
    }


    public static QueryResult getUsersByExp(String guild_id, int limit) {
        if (limit == 0) {
            return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC;");
        }
        return instance.query("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC limit " + limit + ";");
    }



    public static boolean toggleLevelUp(String guild_id, boolean toggle) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, exp_enabled) VALUES ('" + guild_id + "', '" + (toggle ? "1" : "0") + "') ON DUPLICATE KEY UPDATE exp_enabled = '" + (toggle ? "1" : "0") + "';");
    }

    public static boolean toggleBlacklist(String guild_id, boolean toggle) {
        return instance.defaultQuery("UPDATE guild SET blacklist_enabled = '" + toggle + "' WHERE guild_id = '" + guild_id + "';");
    }


    public static boolean setPrefix(String guild_id, String prefix) {
        return instance.defaultQuery("INSERT INTO guild(guild_id, prefix)" + "VALUES('" + guild_id + "','" + prefix +"') ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';");
    }

    public static boolean updatePrefix(String guild_id, String prefix) {
        return instance.defaultQuery("UPDATE guild SET prefix = '" + prefix + "' WHERE guild_id = '" + guild_id + "';");
    }

    public static boolean setGreet(String user_id, String guild_id, String sound_id) {
        return instance.defaultQuery("INSERT INTO greeting (user_id, guild_id, sound_id) VALUES ('" + user_id + "', '" + guild_id + "', '" + sound_id + "') ON DUPLICATE KEY UPDATE sound_id = '" + sound_id + "';");
    }

    public static boolean deleteGreet(String user_id, String guild_id) {
        return instance.defaultQuery("DELETE from greeting WHERE guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';");
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

    public static QueryResult getGuildByThreshold(int threshold, String guild_id){
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

    public static QueryResult getAlertsRoles(String guild_id) {
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

    public static QueryResult getAlerts(String guild_id) {
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
            QueryResult result = instance.query("SELECT id, role_id FROM alert_role WHERE alert_id = '" + valueOf + "';");
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

    public static QueryResult getChannelData(String guild_id) {
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


    public static QueryResult getAliases(String user_id) {
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



    public static HashMap<String, QueryResult> getCustomCommandData(String guild_id) {
        HashMap<String, QueryResult> commandData = new HashMap<>();
        QueryResult result = instance.query("SELECT ID,name,description,slash FROM commands WHERE guild_id = " + guild_id);

        if (result.isEmpty()) {
            return null;
        }
        commandData.put("commands", result);

        String ids = String.join(", ", result.arrayColumn("ID"));

        QueryResult optionResult = instance.query("SELECT ID,command_id,`key`,description,required,type FROM command_option WHERE command_id IN (" + ids + ")");
        commandData.put("options", optionResult);

        QueryResult valueResult = null;
        if (!optionResult.isEmpty()) {
            String optionIds = String.join(", ", optionResult.arrayColumn("ID"));
            valueResult = instance.query("SELECT ID,option_id,`key`,value FROM command_option_value WHERE option_id IN (" + optionIds + ")");
            commandData.put("values", valueResult);
        }

        QueryResult taskResult = instance.query("SELECT ID,command_id,type,`order` FROM command_task WHERE command_id IN (" + ids + ") order by `order`");
        commandData.put("tasks", taskResult);

        String taskIds = String.join(", ", taskResult.arrayColumn("ID"));
        QueryResult taskValueResult = instance.query("SELECT ID,task_id,value,from_option FROM command_task_value WHERE task_id IN (" + taskIds + ")");
        commandData.put("task_values", taskValueResult);

        String taskValueIds = String.join(", ", taskValueResult.arrayColumn("ID"));
        QueryResult taskMessage = instance.query("SELECT ID,task_value_id,message FROM command_task_message WHERE task_value_id IN (" + taskValueIds + ")");
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

    public static QueryResult getTwitchSubscriptions(String streamer_id) {
        return instance.query("SELECT a.guild_id as guild_id from alert_twitch as at join alert as a on at.alert_id = a.id WHERE at.streamer_id = '" + streamer_id + "';");
    }

    public static QueryResult getSoundTags(String sound_id) {
        return instance.query("SELECT ts.tag_id as id,t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id = '" + sound_id + "';");
    }

    public static QueryResult getSoundsTags(String ...sound_id) {
        StringBuilder sb = new StringBuilder();
        for(String sound : sound_id) {
            sb.append("'" + sound + "', ");
        }
        sb.setLength(sb.length() - 2);
        return instance.query("SELECT ts.sound_id as sound_id, ts.tag_id as tag_id, t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id IN (" + sb.toString() + ");");
    }

    public static QueryRecord getTag(String tag_id) {
        return instance.lineQuery("SELECT id, name FROM tag WHERE id = '" + tag_id + "';");
    }

    public static boolean setSoundTags(String sound_id, List<Tag> tags) {
        String values = "";
        for(Tag tag : tags) {
            if (tag.getId() != 0) values += "('" + sound_id + "', '" + tag.getId() + "'), ";
        }
        if (!values.isEmpty()) {
            values = values.substring(0, values.length() - 2);
        }
        instance.defaultQuery("DELETE FROM tag_sounds WHERE sound_id = '" + sound_id + "';");
        return instance.defaultQuery("INSERT INTO tag_sounds(sound_id, tag_id) VALUES " + values + ";");
    }

    public static int insertTag(String tag) {
        int id = 0;

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM tag WHERE name = ?;");
            ps.setString(1, tag);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = rs.getInt("id");
            } else{
                ps = c.prepareStatement("INSERT INTO tag(name) VALUES(?);");
                ps.setString(1, tag);
                ps.executeUpdate();
                id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
                c.commit();
            }
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

    public static QueryRecord getLikeDislike(String sound_id) {
        return instance.lineQuery("SELECT "
            + "(SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = 1) AS likes, "
            + "(SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = -1) AS dislikes;");
    }


    public static boolean setLikeDislike(String sound_id, String user_id, int value) {
        return instance.defaultQuery("INSERT INTO sound_interactions(sound_id, user_id, value) VALUES('" + sound_id + "', '" + user_id + "', '" + value + "') ON DUPLICATE KEY UPDATE value = '" + value + "';", "UPDATE sound SET likes = (SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = 1), dislikes = (SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = -1) WHERE id = '" + sound_id + "';");
    }

    public static QueryRecord hasInterectedSound(String sound_id, String user_id) {
        String query = "SELECT " +
                       "CASE WHEN value = 1 THEN 1 ELSE 0 END AS `like`, " +
                       "CASE WHEN value = -1 THEN 1 ELSE 0 END AS `dislike` " +
                       "FROM sound_interactions " +
                       "WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "';";
        return instance.lineQuery(query);
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

    public static QueryResult getPlaylists(String user_id) {
        return instance.query("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "';");
    }

    public static QueryRecord getPlaylist(String user_id, int playlist_id) {
        return instance.lineQuery("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "' AND id = " + playlist_id + ";");
    }

    public static QueryResult getPlaylistsWithSize(String user_id) {
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

    public static QueryResult getPlaylistTracks(int playlist_id, Integer limit, Integer page) {
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

    public static QueryResult getWarnings(String valueOf) {
        return instance.query("SELECT id, action, action_role, action_time, infractions, infractions_time FROM automated_action WHERE guild_id = '" + valueOf + "' ORDER BY infractions DESC");
    }

    public static QueryResult getAutomatedActionsExpiring() {
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

    public static QueryResult test() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("name", "eee");
        values.put("guild_id", "608967318789160970");
        values.put("user_id", "383358222972616705");
        values.put("extension", "mp3");
        return instance.insert("sound", values);
    }

}
