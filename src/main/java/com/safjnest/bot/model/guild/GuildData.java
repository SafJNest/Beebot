package com.safjnest.bot.model.guild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.safjnest.bot.Bot;
import com.safjnest.bot.audio.tts.TTSVoices;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertKey;
import com.safjnest.bot.model.guild.alert.AlertProblem;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.status.log.BotLogger;
import com.safjnest.status.log.LoggerIDpair;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;

/**
 * Class that stores all the settings for a guild.
 * <ul>
 * <li>Prefix</li>
 * <li>ID</li>
 * </ul>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class GuildData {

    private static final List<String> SINGLE_ALERTS = List.of("welcome", "leave", "boost", "levelUp");

    private final String ID;
    private final BotDB database;

    private String prefix;

    private boolean experience;

    private String voice;
    private String language;

    private String mutedRoleId;

    private final BlacklistData blacklistData;

    private LinkedHashMap<Integer, AlertData> alerts;

    private final HashMap<String, ChannelData> channels = new LinkedHashMap<>();
    private final HashMap<String, MemberData> members = new HashMap<>();
    private final List<AutomatedAction> actions = new ArrayList<>();

    private LeagueShard leagueShard;
    private RegionShard reagionShard;

    private final LoggerIDpair loggerIDpair;

    private GuildData original;
    private boolean stored;
    private long revision;

    public record Settings(String id, String name, String iconUrl, List<Map<String, Object>> channels,
            List<Map<String, Object>> roles, Map<String, Object> settings) {}

    public record Error(String field, String code, String message) {}

    public record Result(String status, List<Error> errors, AlertState alert) {}

    public record AlertState(Integer id, boolean exists, boolean enabled, boolean valid,
            String message, String privateMessage, String channelId, String sendType, List<String> roleIds,
            String streamerId, String roleId, Integer level, Boolean temporary, List<AlertProblem> problems) {

        public static AlertState from(AlertData alert, boolean experienceEnabled) {
            if (alert == null)
                return new AlertState(null, false, false, false, null, null, null, null,
                    List.of(), null, null, null, null, List.of());

            return alert.snapshot(experienceEnabled);
        }
    }

    public GuildData(String id) {
        this(id, Bot.getPrefix(), BotDB.get());
    }

    private GuildData(String id, String defaultPrefix, BotDB database) {
        this.ID = id;
        this.database = database;

        this.prefix = defaultPrefix;
        this.experience = true;

        this.voice = "John";
        this.language = "en-us";

        this.leagueShard = LeagueShard.EUW1;
        this.reagionShard = leagueShard.toRegionShard();

        this.loggerIDpair = new LoggerIDpair(id, LoggerIDpair.IDType.GUILD);

        this.blacklistData = new BlacklistData(this);
    }

    public GuildData(QueryRecord row) {
        this(row, BotDB.getChannelData(row.get("guild_id")), BotDB.getWarnings(row.get("guild_id")), BotDB.get());
    }

    private GuildData(QueryRecord row, List<QueryRecord> channelRows, List<QueryRecord> actionRows, BotDB database) {
        this.ID = row.get("guild_id");
        this.database = database;

        this.prefix = row.get("prefix");
        this.experience = row.getAsBoolean("exp_enabled");

        this.voice = row.get("name_tts") == null ? "John" : row.get("name_tts");
        this.language = row.get("language_tts") == null ? "en-us" : row.get("language_tts");

        this.leagueShard = LeagueShard.values()[row.getAsInt("league_shard")];
        this.reagionShard = leagueShard.toRegionShard();

        this.loggerIDpair = new LoggerIDpair(ID, LoggerIDpair.IDType.GUILD);

        this.blacklistData = new BlacklistData(
            row.getAsInt("threshold"),
            row.get("blacklist_channel"),
            row.getAsBoolean("blacklist_enabled"),
            this
        );

        this.stored = true;

        for (QueryRecord channel : channelRows)
            channels.put(channel.get("channel_id"), new ChannelData(channel, database));

        for (QueryRecord action : actionRows)
            actions.add(new AutomatedAction(action));
    }

    private GuildData(GuildData original) {
        this(original.ID, original.prefix, original.database);

        this.experience = original.experience;

        this.voice = original.voice;
        this.language = original.language;

        this.leagueShard = original.leagueShard;
        this.reagionShard = original.reagionShard;

        this.stored = original.stored;
        this.revision = original.revision;
        this.original = original;
    }

    public static GuildData defaults(String id, String prefix, BotDB database) {
        return new GuildData(id, prefix, database);
    }

    public static GuildData fromRows(QueryRecord row, List<QueryRecord> channels, List<QueryRecord> actions) {
        return fromRows(row, channels, actions, BotDB.get());
    }

    public static GuildData fromRows(QueryRecord row, List<QueryRecord> channels, List<QueryRecord> actions, BotDB database) {
        return new GuildData(row, channels, actions, database);
    }

//     ▄██████▄  ███    █▄   ▄█   ▄█       ████████▄
//    ███    ███ ███    ███ ███  ███       ███   ▀███
//    ███    █▀  ███    ███ ███▌ ███       ███    ███
//   ▄███        ███    ███ ███▌ ███       ███    ███
//  ▀▀███ ████▄  ███    ███ ███▌ ███       ███    ███
//    ███    ███ ███    ███ ███  ███       ███    ███
//    ███    ███ ███    ███ ███  ███▌    ▄ ███   ▄███
//    ████████▀  ████████▀  █▀   █████▄▄██ ████████▀
//                               ▀

    public synchronized GuildData deferEdit() {
        if (original != null)
            throw new IllegalStateException("This guild is already an edit");

        return new GuildData(this);
    }

    public boolean update() {
        if (original == null)
            return deferEdit().update();

        synchronized (original) {
            if (revision != original.revision)
                return false;

            try {
                database.saveGeneral(this);
            } catch (IllegalStateException exception) {
                return false;
            }

            original.prefix = prefix;
            original.experience = experience;

            original.voice = voice;
            original.language = language;

            original.leagueShard = leagueShard;
            original.reagionShard = reagionShard;

            original.stored = true;
            original.revision++;

            return true;
        }
    }

    public synchronized void ensureStored() {
        if (stored)
            return;

        database.ensureGuild(ID, prefix);
        stored = true;
    }

    public String getId() {
        return ID;
    }

    BotDB getDatabase() {
        return database;
    }

    public synchronized String getPrefix() {
        return prefix;
    }

    public synchronized boolean isExperienceEnabled() {
        return experience;
    }

    public boolean setPrefix(String prefix) {
        return change(edit -> edit.prefix = prefix);
    }

    public boolean setExpSystem(boolean enabled) {
        return change(edit -> edit.experience = enabled);
    }

    public synchronized LeagueShard getLeagueShard() {
        return leagueShard;
    }

    public synchronized LeagueShard getLeagueShard(String channelId) {
        LeagueShard shard = getChannelData(channelId).getLeagueShard();

        return shard == null ? leagueShard : shard;
    }

    public synchronized RegionShard getRegionShard() {
        return reagionShard;
    }

    public boolean setLeagueShard(LeagueShard shard) {
        return change(edit -> {
            edit.leagueShard = shard;
            edit.reagionShard = shard.toRegionShard();
        });
    }

    public synchronized void setRegionShard(RegionShard shard) {
        reagionShard = shard;
    }

    public synchronized String getVoice() {
        return voice;
    }

    public synchronized String getLanguage() {
        return language;
    }

    public boolean setVoice(String voice) {
        String language = TTSVoices.getLanguage(voice);

        if (language == null)
            return false;

        return change(edit -> {
            edit.voice = voice;
            edit.language = language;
        });
    }

    public synchronized Map<String, String> getSettings(List<String> keys) {
        Map<String, String> values = new HashMap<>();

        for (String key : keys) {
            switch (key) {
                case "prefix" -> values.put(key, prefix);
                case "expSystem" -> values.put(key, String.valueOf(experience));
                case "voice" -> {
                    values.put("voice", voice);
                    values.put("language", language);
                }
                case "leagueShard" -> values.put(key, String.valueOf(leagueShard.ordinal()));
                default -> System.out.println("Unknown setting: " + key);
            }
        }

        return values;
    }

    public synchronized boolean setSettings(Map<String, String> values) {
        if (values == null || values.isEmpty())
            return false;

        GuildData edit = deferEdit();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            switch (entry.getKey()) {
                case "prefix" -> edit.setPrefix(entry.getValue());
                case "expSystem" -> edit.setExpSystem(Boolean.parseBoolean(entry.getValue()));
                case "voice" -> {
                    if (!edit.setVoice(entry.getValue()))
                        return false;
                }
                case "leagueShard" -> edit.setLeagueShard(LeagueShard.values()[Integer.parseInt(entry.getValue())]);
                default -> System.out.println("Unknown setting: " + entry.getKey());
            }
        }

        return edit.update();
    }

//     ▄████████  ▄█          ▄████████    ▄████████     ███          ████████▄     ▄████████     ███        ▄████████
//    ███    ███ ███         ███    ███   ███    ███ ▀█████████▄      ███   ▀███   ███    ███ ▀█████████▄   ███    ███
//    ███    ███ ███         ███    █▀    ███    ███    ▀███▀▀██      ███    ███   ███    ███    ▀███▀▀██   ███    ███
//    ███    ███ ███        ▄███▄▄▄      ▄███▄▄▄▄██▀     ███   ▀      ███    ███   ███    ███     ███   ▀   ███    ███
//  ▀███████████ ███       ▀▀███▀▀▀     ▀▀███▀▀▀▀▀       ███          ███    ███ ▀███████████     ███     ▀███████████
//    ███    ███ ███         ███    █▄  ▀███████████     ███          ███    ███   ███    ███     ███       ███    ███
//    ███    ███ ███▌    ▄   ███    ███   ███    ███     ███          ███   ▄███   ███    ███     ███       ███    ███
//    ███    █▀  █████▄▄██   ██████████   ███    ███    ▄████▀        ████████▀    ███    █▀     ▄████▀     ███    █▀
//               ▀                        ███    ███
//

    public synchronized HashMap<AlertKey<?>, AlertData> getAlerts() {
        loadAlerts();

        HashMap<AlertKey<?>, AlertData> result = new LinkedHashMap<>();

        for (AlertData alert : alerts.values())
            result.put(alert.getKey(), alert);

        return result;
    }

    public synchronized AlertData getAlert(AlertType type) {
        return type == AlertType.REWARD ? getHigherReward(0) : findAlert(type, null);
    }

    public synchronized AlertData findAlert(AlertType type, String key) {
        loadAlerts();

        for (AlertData alert : alerts.values()) {
            if (alert.getType() != type)
                continue;

            if (alert instanceof TwitchData twitch && !twitch.getStreamer().equals(key))
                continue;

            if (alert instanceof RewardData reward && !String.valueOf(reward.getLevel()).equals(key))
                continue;

            return alert;
        }

        return null;
    }

    public AlertData getAlertByID(String id) {
        return id == null || id.isEmpty() ? null : getAlertByID(Integer.parseInt(id));
    }

    public synchronized AlertData getAlertByID(int id) {
        loadAlerts();

        return alerts.get(id);
    }

    public synchronized RewardData getAlert(AlertType type, int level) {
        AlertData alert = findAlert(type, String.valueOf(level));

        return alert instanceof RewardData reward ? reward : null;
    }

    public synchronized RewardData getLowerReward(int level) {
        for (int current = level - 1; current >= 0; current--) {
            RewardData reward = getAlert(AlertType.REWARD, current);

            if (reward != null)
                return reward;
        }

        return null;
    }

    public synchronized RewardData getHigherReward(int level) {
        for (int current = level + 1; current <= 100; current++) {
            RewardData reward = getAlert(AlertType.REWARD, current);

            if (reward != null)
                return reward;
        }

        return null;
    }

    public synchronized List<RewardData> getRewards() {
        List<RewardData> result = new ArrayList<>();

        for (int level = 1; level <= 100; level++) {
            RewardData reward = getAlert(AlertType.REWARD, level);

            if (reward != null)
                result.add(reward);
        }

        return result;
    }

    public AlertData getWelcome() {
        return getAlert(AlertType.WELCOME);
    }

    public synchronized AlertData newAlert(AlertType type, String key) {
        loadAlerts();

        return AlertData.draft(ID, type, key, prefix, database);
    }

    public synchronized void addAlert(AlertData alert) {
        if (!ID.equals(alert.getGuildId()) || alert.getID() == 0)
            throw new IllegalArgumentException("Only a saved alert from this guild can be added");

        loadAlerts();

        AlertData current = findAlert(alert.getType(), alert instanceof TwitchData twitch ? twitch.getStreamer()
            : alert instanceof RewardData reward ? String.valueOf(reward.getLevel()) : null);

        if (current != null && current != alert)
            throw new IllegalStateException("This alert is already cached");

        alerts.put(alert.getID(), alert);
        stored = true;
    }

    public synchronized AlertData createAlert(AlertType type, String key, Consumer<AlertData> configure) {
        AlertData existing = findAlert(type, key);

        if (existing != null)
            return existing;

        AlertData edit = newAlert(type, key);
        configure.accept(edit);

        if (!edit.update())
            return null;

        addAlert(edit);

        return edit;
    }

    public boolean deleteAlert(AlertType type) {
        return deleteAlert(type, 0);
    }

    public synchronized boolean deleteAlert(AlertType type, int level) {
        return deleteAlert(findAlert(type, type == AlertType.REWARD ? String.valueOf(level) : null));
    }

    public synchronized boolean deleteAlert(AlertType type, String key) {
        return deleteAlert(findAlert(type, key));
    }

    public synchronized boolean deleteAlert(AlertData alert) {
        loadAlerts();

        if (alert == null || alerts.get(alert.getID()) != alert || !alert.terminator4LaRinascita())
            return false;

        alerts.remove(alert.getID());

        return true;
    }

    public synchronized TwitchData getTwitchdata(String streamerId) {
        AlertData alert = findAlert(AlertType.TWITCH, streamerId);

        return alert instanceof TwitchData twitch ? twitch : null;
    }

    public synchronized HashMap<AlertKey<String>, TwitchData> getTwitchDatas() {
        loadAlerts();

        HashMap<AlertKey<String>, TwitchData> result = new HashMap<>();

        for (AlertData alert : alerts.values()) {
            if (alert instanceof TwitchData twitch)
                result.put(twitch.getKey(), twitch);
        }

        return result;
    }

//  ▀█████████▄   ▄█          ▄████████  ▄████████    ▄█   ▄█▄  ▄█        ▄█     ▄████████     ███
//    ███    ███ ███         ███    ███ ███    ███   ███ ▄███▀ ███       ███    ███    ███ ▀█████████▄
//    ███    ███ ███         ███    ███ ███    █▀    ███▐██▀   ███       ███▌   ███    █▀     ▀███▀▀██
//   ▄███▄▄▄██▀  ███         ███    ███ ███         ▄█████▀    ███       ███▌   ███            ███   ▀
//  ▀▀███▀▀▀██▄  ███       ▀███████████ ███        ▀▀█████▄    ███       ███▌ ▀███████████     ███
//    ███    ██▄ ███         ███    ███ ███    █▄    ███▐██▄   ███       ███           ███     ███
//    ███    ███ ███▌    ▄   ███    ███ ███    ███   ███ ▀███▄ ███▌    ▄ ███     ▄█    ███     ███
//  ▄█████████▀  █████▄▄██   ███    █▀  ████████▀    ███   ▀█▀ █████▄▄██ █▀    ▄████████▀     ▄████▀
//               ▀                                   ▀         ▀
//

    public BlacklistData getBlacklistData() {
        return blacklistData;
    }

    public synchronized boolean setBlackListData(int threshold, String channelId) {
        BlacklistData edit = blacklistData.deferEdit();
        edit.setThreshold(threshold);
        edit.setBlackChannelId(channelId);
        edit.setBlacklistEnabled(true);

        return edit.update();
    }

    public int getThreshold() {
        return blacklistData.getThreshold();
    }

    public String getBlackChannelId() {
        return blacklistData.getBlackChannelId();
    }

    public boolean setBlackChannel(String channelId) {
        return blacklistData.setBlackChannelId(channelId);
    }

    public boolean setThreshold(int threshold) {
        return blacklistData.setThreshold(threshold);
    }

    public boolean blacklistEnabled() {
        return blacklistData.isBlacklistEnabled();
    }

    public boolean setBlacklistEnabled(boolean enabled) {
        return blacklistData.setBlacklistEnabled(enabled);
    }

//   ▄████████    ▄█    █▄       ▄████████ ███▄▄▄▄   ███▄▄▄▄      ▄████████  ▄█
//  ███    ███   ███    ███     ███    ███ ███▀▀▀██▄ ███▀▀▀██▄   ███    ███ ███
//  ███    █▀    ███    ███     ███    ███ ███   ███ ███   ███   ███    █▀  ███
//  ███         ▄███▄▄▄▄███▄▄   ███    ███ ███   ███ ███   ███  ▄███▄▄▄     ███
//  ███        ▀▀███▀▀▀▀███▀  ▀███████████ ███   ███ ███   ███ ▀▀███▀▀▀     ███
//  ███    █▄    ███    ███     ███    ███ ███   ███ ███   ███   ███    █▄  ███
//  ███    ███   ███    ███     ███    ███ ███   ███ ███   ███   ███    ███ ███▌    ▄
//  ████████▀    ███    █▀      ███    █▀   ▀█   █▀   ▀█   █▀    ██████████ █████▄▄██
//                                                                          ▀

    public synchronized void retriveChannels() {
        List<QueryRecord> rows = database.readGuildChannels(List.of(ID));

        for (QueryRecord row : rows) {
            String id = row.get("channel_id");
            channels.putIfAbsent(id, new ChannelData(row, database));
        }
    }

    public synchronized HashMap<String, ChannelData> getChannels() {
        return new LinkedHashMap<>(channels);
    }

    public synchronized ChannelData getChannelData(String id) {
        return channels.computeIfAbsent(id, key -> new ChannelData(key, ID, prefix, database));
    }

    public synchronized boolean deleteChannelData(String id) {
        ChannelData channel = channels.get(id);

        if (channel == null)
            return true;

        if (!channel.terminator5LaRivolta())
            return false;

        channels.remove(id);

        return true;
    }

    public Boolean getExpSystemRoom(String id) {
        return getChannelData(id).isExpSystemEnabled();
    }

    public Boolean getCommandStatsRoom(String id) {
        return getChannelData(id).getCommand();
    }

    public double getExperienceModifier(String id) {
        return getChannelData(id).getExperienceModifier();
    }

//     ▄████████  ▄████████     ███      ▄█   ▄██████▄  ███▄▄▄▄
//    ███    ███ ███    ███ ▀█████████▄ ███  ███    ███ ███▀▀▀██▄
//    ███    ███ ███    █▀     ▀███▀▀██ ███▌ ███    ███ ███   ███
//    ███    ███ ███            ███   ▀ ███▌ ███    ███ ███   ███
//  ▀███████████ ███            ███     ███▌ ███    ███ ███   ███
//    ███    ███ ███    █▄      ███     ███  ███    ███ ███   ███
//    ███    ███ ███    ███     ███     ███  ███    ███ ███   ███
//    ███    █▀  ████████▀     ▄████▀   █▀    ▀██████▀   ▀█   █▀
//

    public synchronized boolean addAction(int action, String roleId, int actionTime, int infractions, int infractionsTime) {
        AutomatedAction value = AutomatedAction.create(ID, action, roleId, actionTime, infractions, infractionsTime);

        if (value == null)
            return false;

        actions.add(value);

        return true;
    }

    public synchronized HashMap<Integer, AutomatedAction> getActionsWithId() {
        HashMap<Integer, AutomatedAction> result = new HashMap<>();

        for (AutomatedAction action : actions)
            result.put(action.getId(), action);

        return result;
    }

    public synchronized List<AutomatedAction> getActions() {
        return new ArrayList<>(actions);
    }

    public synchronized AutomatedAction getAction(int id) {
        for (AutomatedAction action : actions) {
            if (action.getId() == id)
                return action;
        }

        return null;
    }

    public boolean isBlackListCached() {
        return true;
    }

    public synchronized boolean isAlertsCached() {
        return alerts != null;
    }

    public synchronized AlertState alertState(AlertType type, String key) {
        return AlertState.from(findAlert(type, key), experience);
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("prefix", prefix);
        general.put("experienceEnabled", experience);
        general.put("voice", voice);
        general.put("language", language);
        general.put("leagueShard", leagueShard.name());

        Map<String, Object> channelStates = new LinkedHashMap<>();
        List<ChannelData> orderedChannels = new ArrayList<>(channels.values());
        orderedChannels.sort(Comparator.comparingInt(channel -> Integer.parseInt(channel.getId())));

        for (ChannelData channel : orderedChannels) {
            if (!channel.getId().equals("0"))
                channelStates.put(channel.getRoomId(), channel.snapshot());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("general", Collections.unmodifiableMap(general));
        result.put("blacklist", blacklistData.snapshot());
        result.put("channels", Collections.unmodifiableMap(channelStates));

        for (String name : SINGLE_ALERTS) {
            AlertType type = switch (name) {
                case "welcome" -> AlertType.WELCOME;
                case "leave" -> AlertType.LEAVE;
                case "boost" -> AlertType.BOOST;
                default -> AlertType.LEVEL_UP;
            };

            result.put(name, alertState(type, null));
        }

        Map<String, AlertState> twitchStates = new LinkedHashMap<>();
        Map<String, AlertState> rewardStates = new LinkedHashMap<>();

        for (AlertData alert : alerts.values()) {
            if (alert instanceof TwitchData twitch)
                twitchStates.put(twitch.getStreamer(), twitch.snapshot(experience));

            if (alert instanceof RewardData reward)
                rewardStates.put(String.valueOf(reward.getLevel()), reward.snapshot(experience));
        }

        result.put("twitch", Collections.unmodifiableMap(twitchStates));
        result.put("reward", Collections.unmodifiableMap(rewardStates));

        List<AutomatedAction> warningActions = new ArrayList<>();

        for (AutomatedAction action : actions)
            warningActions.add(new AutomatedAction(action.getId(), action.getAction(), action.getRoleId(),
                action.getActionTime(), action.getInfractions(), action.getInfractionsTime()));

        warningActions.sort(Comparator.comparingInt(AutomatedAction::getId));
        result.put("warning", Map.of("actions", List.copyOf(warningActions)));

        return Collections.unmodifiableMap(result);
    }

    @Override
    public synchronized String toString() {
        return "{ID: " + ID + ", Prefix: " + prefix + ", ExpSystem: " + experience + ", Shard: " + leagueShard + "}";
    }

//     ▄▄▄▄███▄▄▄▄      ▄████████   ▄▄▄▄███▄▄▄▄   ▀█████████▄     ▄████████    ▄████████
//   ▄██▀▀▀███▀▀▀██▄   ███    ███ ▄██▀▀▀███▀▀▀██▄   ███    ███   ███    ███   ███    ███
//   ███   ███   ███   ███    █▀  ███   ███   ███   ███    ███   ███    █▀    ███    ███
//   ███   ███   ███  ▄███▄▄▄     ███   ███   ███  ▄███▄▄▄██▀   ▄███▄▄▄      ▄███▄▄▄▄██▀
//   ███   ███   ███ ▀▀███▀▀▀     ███   ███   ███ ▀▀███▀▀▀██▄  ▀▀███▀▀▀     ▀▀███▀▀▀▀▀
//   ███   ███   ███   ███    █▄  ███   ███   ███   ███    ██▄   ███    █▄  ▀███████████
//   ███   ███   ███   ███    ███ ███   ███   ███   ███    ███   ███    ███   ███    ███
//    ▀█   ███   █▀    ██████████  ▀█   ███   █▀  ▄█████████▀    ██████████   ███    ███
//                                                                            ███    ███

    public String getMutedRoleId() {
        if (mutedRoleId != null && !mutedRoleId.isEmpty())
            return mutedRoleId;

        mutedRoleId = BotDB.getMutedRole(String.valueOf(ID));
        if (mutedRoleId == null)
            mutedRoleId = "";

        if (mutedRoleId.isEmpty()) {
            Guild guild = Bot.getJDA().getGuildById(ID);
            Role role = guild.createRole().setName("Beebot-muted").complete();
            mutedRoleId = role.getId();
            for (TextChannel tc : guild.getTextChannels()) {
                tc.getManager().putRolePermissionOverride(role.getIdLong(), null, Collections.singleton(Permission.MESSAGE_SEND)).queue();

            }
            for (VoiceChannel vc : guild.getVoiceChannels()) {
                vc.getManager().putPermissionOverride(role, null, Collections.singleton(Permission.VOICE_SPEAK)).queue();
            }
        }
        return mutedRoleId;
    }

    public boolean hasMutedRole() {
        if (mutedRoleId == null) {
            mutedRoleId = BotDB.getMutedRole(String.valueOf(ID));
        }
        return mutedRoleId != null && !mutedRoleId.isEmpty();
    }

    public MemberData getMemberData(Long userId) {
        return getMemberData(String.valueOf(userId));
    }

    public synchronized MemberData getMemberData(String userId) {
        MemberData member = this.members.get(userId);
        if (member != null) {
            return member;
        }

        member = retriveMemberData(userId);
        if (member == null) {
            member = new MemberData(userId, this.getId(), prefix, database);
        }
        this.members.put(userId, member);

        return member;
    }

    public synchronized HashMap<String, MemberData> getMembers() {
        return new HashMap<>(members);
    }

    public boolean canReceiveExperience(long userId, String channelId) {
        return this.isExperienceEnabled() && this.getExpSystemRoom(channelId) && getMemberData(userId).canReceiveExperience();
    }

    /* -------------------------------------------------------------------------- */

    private synchronized boolean change(Consumer<GuildData> mutation) {
        if (original != null) {
            mutation.accept(this);

            return true;
        }

        GuildData edit = deferEdit();
        mutation.accept(edit);

        return edit.update();
    }

    private void loadAlerts() {
        if (alerts == null) {
            List<AlertData> rows = database.readGuildAlerts(ID);
            LinkedHashMap<Integer, AlertData> loaded = new LinkedHashMap<>();

            for (AlertData alert : rows)
                loaded.put(alert.getID(), alert);

            alerts = loaded;
        }

        alerts.values().removeIf(AlertData::isDeleted);
    }

    private MemberData retriveMemberData(String userId) {
        MemberData member = null;
        QueryRecord result = database.readMember(ID, userId);
        if (result.emptyValues()) {
            return null;
        }

        member = new MemberData(result, prefix, database);
        return member;
    }

}
