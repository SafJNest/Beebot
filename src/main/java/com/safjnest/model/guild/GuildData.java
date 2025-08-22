package com.safjnest.model.guild;



import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;

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

    private final String ID;

    private String prefix;


    private boolean experience;

    private String voice;
    private String language;

    private String mutedRoleId;


    private BlacklistData blacklistData;

    private HashMap<AlertKey<?>, AlertData> alerts;

    private HashMap<String, ChannelData> channels;
    private HashMap<String, MemberData> members;
    private List<AutomatedAction> actions;

    private LeagueShard leagueShard;
    private RegionShard reagionShard;

    private LoggerIDpair loggerIDpair;

    public GuildData(String id) {
        this.ID = id;
        this.prefix = Bot.getPrefix();
        this.experience = true;

        this.members = new HashMap<>();
        this.channels = new HashMap<>();

        this.loggerIDpair = new LoggerIDpair(String.valueOf(ID), LoggerIDpair.IDType.GUILD);

        this.leagueShard = LeagueShard.EUW1;
        this.reagionShard = this.leagueShard.toRegionShard();

        this.blacklistData = new BlacklistData(this);

        this.voice = "John";
        this.language = "en-us";
    }

    public GuildData(QueryRecord data) {
        this.ID = data.get("guild_id");
        this.prefix = data.get("prefix");
        this.experience = data.getAsBoolean("exp_enabled");

        this.members = new HashMap<>();

        this.loggerIDpair = new LoggerIDpair(String.valueOf(ID), LoggerIDpair.IDType.GUILD);

        this.leagueShard = LeagueShard.values()[Integer.parseInt(data.get("league_shard"))];
        this.reagionShard = this.leagueShard.toRegionShard();

        this.voice = data.get("name_tts") != null ? data.get("name_tts") : "John";
        this.language = data.get("language_tts") != null ? data.get("language_tts") : "en-us";

        this.blacklistData = new BlacklistData(
            data.getAsInt("threshold"),
            data.get("blacklist_channel") != null ? data.get("blacklist_channel") : null,
            data.getAsBoolean("blacklist_enabled"),
            this
        );

        retriveChannels();
        retriveActions();
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

    public String getId() {
        return ID;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isExperienceEnabled() {
        return experience;
    }


    public synchronized boolean setPrefix(String prefix) {
        boolean result = DatabaseHandler.updatePrefix(String.valueOf(ID), prefix);
        if (result) {
            this.prefix = prefix;
        }
        return result;
    }

    public synchronized boolean setExpSystem(boolean expSystem) {
        boolean result = DatabaseHandler.toggleLevelUp(String.valueOf(this.ID), expSystem);
        if (result) {
            this.experience = expSystem;
        }
        return result;
    }

    public LeagueShard getLeagueShard() {
        return this.leagueShard;
    }

    public LeagueShard getLeagueShard(String channelId) {
        LeagueShard shard = getChannelData(channelId).getLeagueShard();
        if (shard != null) return shard;
        return this.leagueShard;
    }

    public RegionShard getRegionShard() {
        return this.reagionShard;
    }

    public synchronized boolean setLeagueShard(LeagueShard shard) {
        boolean result = DatabaseHandler.updateShard(String.valueOf(ID), shard);
        if (result) {
            this.leagueShard = shard;
            setRegionShard(shard.toRegionShard());
        }
        return result;
    }

    public synchronized void setRegionShard(RegionShard shard) {
        this.reagionShard = shard;
    }

    public String getVoice() {
        return this.voice;
    }

    public String getLanguage() {
        return this.language;
    }

    public synchronized boolean setVoice(String voice) {
        String language = TTSVoices.getLanguage(voice);
        if(language == null) {
            return false;
        }

        boolean result = DatabaseHandler.updateVoiceGuild(String.valueOf(ID), language, voice);
        if (result) {
            this.voice = voice;
            this.language = language;
        }

        return result;
    }

    public String getMutedRoleId() {
        if (mutedRoleId != null && !mutedRoleId.isEmpty())
            return mutedRoleId;

        mutedRoleId = DatabaseHandler.getMutedRole(String.valueOf(ID));
        if (mutedRoleId == null) mutedRoleId = "";

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
            mutedRoleId = DatabaseHandler.getMutedRole(String.valueOf(ID));
        }
        return mutedRoleId != null && !mutedRoleId.isEmpty();
    }

    public Map<String, String> getSettings(List<String> settings) {
        Map<String, String> result = new HashMap<>();

        for (String setting : settings) {
            switch (setting) {
                case "prefix":
                    result.put("prefix", getPrefix());
                    break;
                case "expSystem":
                    result.put("expSystem", String.valueOf(isExperienceEnabled()));
                    break;
                case "voice":
                    result.put("voice", getVoice());
                    result.put("language", getLanguage());
                    break;
                case "leagueShard":
                    result.put("leagueShard", String.valueOf(getLeagueShard().ordinal())); //.getRealmValue().toUpperCase()
                    break;
                default:
                    System.out.println("Unknown setting: " + setting);
                    break;
            }
        }
        return result;
    }

    public boolean setSettings(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }

        for (String key : settings.keySet()) {
            switch (key) {
                case "prefix":
                    setPrefix(settings.get(key));
                    break;
                case "expSystem":
                    setExpSystem(Boolean.parseBoolean(settings.get(key)));
                    break;
                case "voice":
                    setVoice(settings.get(key));
                    break;
                case "leagueShard":
                    LeagueShard shard = LeagueShard.values()[Integer.parseInt(settings.get(key))];
                    setLeagueShard(shard);
                    break;
                default:
                    System.out.println("Unknown setting: " + key);
                    break;
            }
        }
        return true;
    }

    public String toString(){
        return "{ID: " + ID + ", Prefix: " + prefix + ", ExpSystem: " + experience + ", Shard: " + leagueShard + "}";
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

    /**
     * If the {@link #alerts alerts map} is null, it will be retrieved from the database and cached.
     * <p>
     * The key is the {@link AlertType type} of the alert.
     * </p>
     * <p>
     * If the alert is {@link AlertType#WELCOME WELCOME}, the will be also retrieved the roles, otherwise it will be null.
     * @return
     */
    public HashMap<AlertKey<?>, AlertData> getAlerts() {
        if (this.alerts == null) {
            BotLogger.debug("Retriving AlertData from database => {0}", loggerIDpair);
            this.alerts = new HashMap<>();
            QueryCollection alertResult = DatabaseHandler.getAlerts(String.valueOf(ID));
            QueryCollection roleResult = DatabaseHandler.getAlertsRoles(String.valueOf(ID));

            HashMap<Integer, HashMap<Integer, String>> roles = new HashMap<>();
            for (QueryRecord row : roleResult) {
                int alertId = row.getAsInt("alert_id");
                String roleId = row.get("role_id");
                int rowId = row.getAsInt("row_id");
                if (!roles.containsKey(alertId)) {
                    roles.put(alertId, new HashMap<>());
                }
                roles.get(alertId).put(rowId, roleId);
            }

            for (QueryRecord row : alertResult) {
                switch (AlertType.getFromOrdinal(row.getAsInt("type"))) {
                    case REWARD:
                        RewardData rd = new RewardData(row, roles.get(row.getAsInt("alert_id")));
                        this.alerts.put(rd.getKey(), rd);
                        break;
                    case TWITCH:
                        TwitchData td = new TwitchData(row);
                        this.alerts.put(td.getKey(), td);
                        break;

                    default:
                        AlertData ad = new AlertData(row, roles.get(row.getAsInt("alert_id")));
                        this.alerts.put(ad.getKey(), ad);
                    break;
                }

            }
        }
        return this.alerts;
    }

    public AlertData getAlert(AlertType type) {
        if (type == AlertType.REWARD)
            return getHigherReward(0);
        return getAlerts().get(new AlertKey<>(type));
    }

    public AlertData getAlertByID(String id) {
        return id == null || id.isEmpty() ? null : getAlertByID(Integer.parseInt(id));
    }

    public AlertData getAlertByID(int id) {
        for (AlertData ad : getAlerts().values()) {
            if (ad.getID() == id) {
                return ad;
            }
        }
        return null;
    }

    public RewardData getAlert(AlertType type, int level) {
        AlertKey<?> key = level != 0 ? new AlertKey<Integer>(type, level) : new AlertKey<>(type);
        AlertData alert = getAlerts().get(key);
        if (alert instanceof RewardData) {
            return (RewardData) alert;
        } else {
            return null;
        }
    }

    public RewardData getLowerReward(int level) {
        for (int i = level - 1; i >= 0; i--) {
            RewardData reward = getAlert(AlertType.REWARD, i);
            if (reward != null) {
                return reward;
            }
        }
        return null;
    }

    public RewardData getHigherReward(int level) {
        for (int i = level + 1; i <= 100; i++) {
            RewardData reward = getAlert(AlertType.REWARD, i);
            if (reward != null) {
                return reward;
            }
        }
        return null;
    }

    public List<RewardData> getRewards() {
        List<RewardData> rewards = new ArrayList<>();
        RewardData reward = null;
        int level = 0;
        do {
            reward = getHigherReward(level);
            level = reward.getLevel();
            rewards.add(reward);
        } while (reward != null);

        return rewards;
    }

    public AlertData getWelcome() {
        return getAlert(AlertType.WELCOME);
    }

    public boolean deleteAlert(AlertType type) {
        return deleteAlert(type, 0);
    }

    public boolean deleteAlert(AlertType type, int level) {
        AlertKey<?> key = level != 0 ? new AlertKey<Integer>(type, level) : new AlertKey<>(type);
        AlertData toDelete = getAlerts().remove(key);
        if (toDelete == null) {
            return false;
        }
        return toDelete.terminator4LaRinascita();
    }

    public boolean deleteAlert(AlertType type, String streamerId) {
        AlertData toDelete = getAlerts().remove(new AlertKey<String>(type, streamerId));
        if (toDelete == null) {
            return false;
        }
        return toDelete.terminator4LaRinascita();
    }

    public TwitchData getTwitchdata(String streamerId) {
        AlertData alert = getAlerts().get(new AlertKey<String>(AlertType.TWITCH, streamerId));
        if (alert instanceof TwitchData) {
            return (TwitchData) alert;
        } else {
            return null;
        }
    }

    public HashMap<AlertKey<String>, TwitchData> getTwitchDatas() {
        return getAlerts().values().stream()
            .filter(alert -> alert instanceof TwitchData)
            .map(alert -> (TwitchData) alert)
            .collect(Collectors.toMap(TwitchData::getKey, twitch -> twitch, (a, b) -> a, HashMap::new));
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
        return this.blacklistData;
    }

    public boolean setBlackListData(int threshold, String blackChannelId) {
        this.blacklistData = new BlacklistData(threshold, blackChannelId, true, this);
        return this.blacklistData.update();
    }


    public int getThreshold() {
        return getBlacklistData().getThreshold();
    }

    public String getBlackChannelId() {
        return getBlacklistData().getBlackChannelId();
    }

    public synchronized boolean setBlackChannel(String blackChannel) {
        return getBlacklistData().setBlackChannelId(blackChannel);
    }

    public synchronized boolean setThreshold(int threshold) {
        return getBlacklistData().setThreshold(threshold);
    }

    public boolean blacklistEnabled() {
        return getBlacklistData().isBlacklistEnabled();
    }

    public boolean setBlacklistEnabled(boolean blacklist_enabled) {
        return getBlacklistData().setBlacklistEnabled(blacklist_enabled);
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

    public void retriveChannels() {
        this.channels = new HashMap<>();
        QueryCollection result = DatabaseHandler.getChannelData(String.valueOf(ID));
        if (result == null) { return; }

        BotLogger.debug("Retriving ChannelData from database => {0}", loggerIDpair);
        for(QueryRecord row : result){
            this.channels.put(
                row.get("channel_id"),
                new ChannelData(row)
            );
        }

    }

    public HashMap<String, ChannelData> getChannels() {
        return this.channels;
    }

    public ChannelData getChannelData(String channel_id) {
        ChannelData cd = this.channels.get(channel_id);
        if (cd == null) {
            cd = new ChannelData(channel_id, this.getId());
            BotLogger.debug("Caching local ChannelData => {0} | {1}", loggerIDpair, new LoggerIDpair(String.valueOf(channel_id), LoggerIDpair.IDType.CHANNEL));
            this.channels.put(channel_id, cd);
        }
        return cd;
    }

    public boolean deleteChannelData(String channel_id) {
        ChannelData toDelete = this.channels.remove(channel_id);
        if (toDelete == null) {
            return true;
        }
        return toDelete.terminator5LaRivolta();
    }


    public Boolean getExpSystemRoom(String id){
        return getChannelData(id).isExpSystemEnabled();
    }

    public Boolean getCommandStatsRoom(String id){
        return getChannelData(id).getCommand();
    }

    public double getExperienceModifier(String id){
        return getChannelData(id).getExperienceModifier();
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

    private MemberData retriveMemberData(String userId) {
        MemberData member = null;
        QueryRecord result = DatabaseHandler.getUserData(String.valueOf(ID), userId);
        if (result.emptyValues()) { return null; }

        BotLogger.debug("Retriving MemberData from database => {0} | {1}", loggerIDpair, new LoggerIDpair(String.valueOf(userId), LoggerIDpair.IDType.USER));
        member = new MemberData(result);
        return member;
    }

    public MemberData getMemberData(Long userId) {
        return getMemberData(String.valueOf(userId));
    }

    public MemberData getMemberData(String userId) {
        MemberData member = this.members.get(userId);
        if (member != null) {
            return member;
        }
        member = retriveMemberData(userId);
        if (member == null) {
            member = new MemberData(userId, this.getId());
            BotLogger.debug("Caching local MemberData => {0} | {1}", loggerIDpair, new LoggerIDpair(String.valueOf(userId), LoggerIDpair.IDType.USER));
        }
        this.members.put(userId, member);
        return member;
    }

    public HashMap<String, MemberData> getMembers() {
        return this.members;
    }

    public boolean canReceiveExperience(long userId, String channelId) {
        return this.isExperienceEnabled() && this.getExpSystemRoom(channelId) && getMemberData(userId).canReceiveExperience();
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

    private void retriveActions() {
        actions = new ArrayList<>();
        QueryCollection result = DatabaseHandler.getWarnings(String.valueOf(ID));
        if (result == null) { return; }

        for (QueryRecord row : result) {
            actions.add(new AutomatedAction(row));
        }
    }

    public boolean addAction(int action, String roleId, int actionTime, int infractions, int infractionsTime) {
        AutomatedAction aa = AutomatedAction.create(String.valueOf(ID), action, roleId, actionTime, infractions, infractionsTime);
        if (aa == null) {
            return false;
        }
        actions.add(aa);
        return true;
    }

    public HashMap <Integer, AutomatedAction> getActionsWithId() {
        HashMap <Integer, AutomatedAction> map = new HashMap<>();
        for (AutomatedAction aa : actions) {
            map.put(aa.getId(), aa);
        }
        return map;
    }

    public List<AutomatedAction> getActions() {
        return actions;
    }

    public AutomatedAction getAction(int id) {
        for (AutomatedAction aa : actions) {
            if (aa.getId() == id) {
                return aa;
            }
        }
        return null;
    }


    public boolean isBlackListCached() {
        return this.blacklistData != null;
    }

    public boolean isAlertsCached() {
        return this.alerts != null;
    }


}
