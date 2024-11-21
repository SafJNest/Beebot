package com.safjnest.model.guild;



import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.model.guild.customcommand.CustomCommand;
import com.safjnest.model.guild.customcommand.Option;
import com.safjnest.model.guild.customcommand.OptionValue;
import com.safjnest.model.guild.customcommand.Task;
import com.safjnest.model.guild.customcommand.TaskType;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
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

    private final Long ID;    

    private String prefix;
    

    private boolean experience;

    private String voice;
    private String language;

    private String mutedRoleId;


    private BlacklistData blacklistData;

    private HashMap<AlertKey<?>, AlertData> alerts;
    
    private HashMap<Long, ChannelData> channels;
    private HashMap<String, MemberData> members;
    private HashMap<String, CustomCommand> customCommands;
    private List<AutomatedAction> actions;

    private LeagueShard leagueShard;
    private RegionShard reagionShard;

    private LoggerIDpair loggerIDpair;

    public GuildData(Long id) {
        this.ID = id;
        this.prefix = Bot.getPrefix();
        this.experience = true;

        this.members = new HashMap<>();
        this.channels = new HashMap<>();
        this.customCommands = new HashMap<>();
        
        this.loggerIDpair = new LoggerIDpair(String.valueOf(ID), LoggerIDpair.IDType.GUILD);

        this.leagueShard = LeagueShard.EUW1;
        this.reagionShard = this.leagueShard.toRegionShard();

        this.blacklistData = new BlacklistData(this);

        this.voice = "John";
        this.language = "en-us";
    }

    public GuildData(QueryRecord data) {
        this.ID = data.getAsLong("guild_id");
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
        retriveCustomCommand();
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

    public Long getId() {
        return ID;
    }

    public String getID() {
        return String.valueOf(ID);
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

    public synchronized boolean setVoice(String voice, String language) {
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
        return getAlerts().get(new AlertKey<>(type));
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
        AlertData alert = getAlerts().get(new AlertKey<Integer>(type, level));
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

    public AlertData getWelcome() {
        return getAlert(AlertType.WELCOME);
    }

    public boolean deleteAlert(AlertType type) {
        return deleteAlert(type, 0);
    }

    public boolean deleteAlert(AlertType type, int level) {
        AlertData toDelete = getAlerts().remove(new AlertKey<Integer>(type, level));
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
                row.getAsLong("channel_id"),
                new ChannelData(row)
            );
        }

    }

    public HashMap<Long, ChannelData> getChannels() {
        return this.channels;
    }

    public ChannelData getChannelData(long channel_id) {
        ChannelData cd = this.channels.get(channel_id);
        if (cd == null) {
            cd = new ChannelData(channel_id, this.getID());
            BotLogger.debug("Caching local ChannelData => {0} | {1}", loggerIDpair, new LoggerIDpair(String.valueOf(channel_id), LoggerIDpair.IDType.CHANNEL));
            this.channels.put(channel_id, cd);
        }
        return cd;
    }

    public ChannelData getChannelData(String channel_id) {
        return getChannelData(Long.parseLong(channel_id));
    }

    public boolean deleteChannelData(String channel_id) {
        ChannelData toDelete = this.channels.remove(Long.parseLong(channel_id));
        if (toDelete == null) {
            return true;
        }
        return toDelete.terminator5LaRivolta();
    }


    public Boolean getExpSystemRoom(Long id){
        return getChannelData(id).isExpSystemEnabled();
    }

    public Boolean getCommandStatsRoom(Long id){
        return getChannelData(id).getCommand();
    }

    public double getExperienceModifier(Long id){
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
            member = new MemberData(userId, this.getID());
            BotLogger.debug("Caching local MemberData => {0} | {1}", loggerIDpair, new LoggerIDpair(String.valueOf(userId), LoggerIDpair.IDType.USER));
        }
        this.members.put(userId, member);
        return member;
    }

    public HashMap<String, MemberData> getMembers() {
        return this.members;
    }

    public boolean canReceiveExperience(long userId, long channelId) {
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








//   ▄████████  ▄██████▄    ▄▄▄▄███▄▄▄▄     ▄▄▄▄███▄▄▄▄      ▄████████ ███▄▄▄▄   ████████▄  
//  ███    ███ ███    ███ ▄██▀▀▀███▀▀▀██▄ ▄██▀▀▀███▀▀▀██▄   ███    ███ ███▀▀▀██▄ ███   ▀███ 
//  ███    █▀  ███    ███ ███   ███   ███ ███   ███   ███   ███    ███ ███   ███ ███    ███ 
//  ███        ███    ███ ███   ███   ███ ███   ███   ███   ███    ███ ███   ███ ███    ███ 
//  ███        ███    ███ ███   ███   ███ ███   ███   ███ ▀███████████ ███   ███ ███    ███ 
//  ███    █▄  ███    ███ ███   ███   ███ ███   ███   ███   ███    ███ ███   ███ ███    ███ 
//  ███    ███ ███    ███ ███   ███   ███ ███   ███   ███   ███    ███ ███   ███ ███   ▄███ 
//  ████████▀   ▀██████▀   ▀█   ███   █▀   ▀█   ███   █▀    ███    █▀   ▀█   █▀  ████████▀  
//                                                                                          


    private void retriveCustomCommand() {
        customCommands = new HashMap<>();
        HashMap<String, QueryCollection> commandData = DatabaseHandler.getCustomCommandData(String.valueOf(ID));
        if (commandData == null) { return; }

        QueryCollection result = commandData.get("commands");
        QueryCollection optionResult = commandData.get("options");
        QueryCollection taskResult = commandData.get("tasks");
        QueryCollection taskValueResult = commandData.get("task_values");
        QueryCollection taskMessage = commandData.get("task_messages");


        for (QueryRecord row : result) {
            int id = row.getAsInt("ID");
            String name = row.get("name");
            String description = row.get("description");
            boolean isSlash = row.getAsBoolean("slash");

            CustomCommand cc = new CustomCommand(id, name, description, isSlash);

            final QueryCollection finalValueResult = commandData.get("values");
            optionResult.stream()
                .filter(optionRow -> optionRow.getAsInt("command_id") == id)
                .forEach(optionRow -> {
                    int optionId = optionRow.getAsInt("ID");
                    String key = optionRow.get("key");
                    String optionDescription = optionRow.get("description");
                    boolean isRequired = optionRow.getAsBoolean("required");
                    OptionType type = OptionType.fromKey(Integer.parseInt(optionRow.get("type")));
            
                    Option option;
                    if (!finalValueResult.isEmpty()) {
                        List<OptionValue> values = finalValueResult.stream()
                            .filter(valueRow -> valueRow.getAsInt("option_id") == optionId)
                            .map(valueRow -> new OptionValue(valueRow.getAsInt("ID"), valueRow.get("key"), valueRow.get("value")))
                            .collect(Collectors.toList());
            
                        option = new Option(optionId, key, optionDescription, isRequired, values);
                    } else {
                        option = new Option(optionId, key, optionDescription, isRequired, type);
                    }
            
                    cc.addOption(option);
                });
            
            taskResult.stream()
                .filter(taskRow -> taskRow.getAsInt("command_id") == id)
                .forEach(taskRow -> {
                    int taskId = taskRow.getAsInt("ID");
                    TaskType type = TaskType.fromValue(taskRow.getAsInt("type"));
                    Task task = new Task(taskId, type);
            
                    taskValueResult.stream()
                        .filter(taskValueRow -> taskValueRow.getAsInt("task_id") == taskId)
                        .forEach(taskValueRow -> {
                            boolean fromOption = taskValueRow.getAsBoolean("from_option");
                            String value = taskValueRow.get("value");
                            if (fromOption) {
                                value = "#" + value;
                            }
                            if (type == TaskType.SEND_MESSAGE) {
                                value = taskMessage.stream()
                                    .filter(messageRow -> messageRow.getAsInt("task_value_id") == taskValueRow.getAsInt("ID"))
                                    .map(messageRow -> messageRow.get("message"))
                                    .findFirst()
                                    .orElse(value);
                            }
                            task.addValue(value);
                        });
                    cc.addTask(task);
                });
            customCommands.put(name, cc);
        }

        updateCommands();
    }

    private void updateCommands() {
        Guild g = Bot.getJDA().getGuildById(ID);
        List<SlashCommandData> commands = new ArrayList<>();
        for (CustomCommand cc : customCommands.values()) {
            if (!cc.isSlash()) {
                continue;
            }

            if (cc.getOptions().isEmpty()) {
                SlashCommandData scd = Commands.slash(cc.getName(), cc.getDescription());
                commands.add(scd);
                continue;
            }

            cc.getOptions().forEach(option -> {
                SlashCommandData scd = Commands.slash(cc.getName(), cc.getDescription());
                scd.addOption(option.getType(), option.getKey(), option.getDescription());
                commands.add(scd);
            });
        }
        g.updateCommands().addCommands(commands).queue();
    }


    public CustomCommand getCustomCommand(String name) {
        return customCommands.get(name);
    }










    public boolean isBlackListCached() {
        return this.blacklistData != null;
    }

    public boolean isAlertsCached() {
        return this.alerts != null;
    }

    
}
