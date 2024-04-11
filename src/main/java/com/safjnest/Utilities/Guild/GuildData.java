package com.safjnest.Utilities.Guild;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.safjnest.Bot;
import com.safjnest.Utilities.Guild.Alert.AlertData;
import com.safjnest.Utilities.Guild.Alert.AlertKey;
import com.safjnest.Utilities.Guild.Alert.AlertType;
import com.safjnest.Utilities.Guild.Alert.RewardData;
import com.safjnest.Utilities.Guild.CustomCommand.CustomCommand;
import com.safjnest.Utilities.Guild.CustomCommand.Option;
import com.safjnest.Utilities.Guild.CustomCommand.OptionValue;
import com.safjnest.Utilities.Guild.CustomCommand.Task;
import com.safjnest.Utilities.Guild.CustomCommand.TaskType;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;


/**
 * Class that stores all the settings for a guild.
 * <ul>
 * <li>Prefix</li>
 * <li>ID</li>
 * </ul>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class GuildData {
    /**
     * Server ID 
     */
    private final Long ID;    
    /**
     * Prefix Server 
     */
    private String prefix;
    
    /**
     * Exp System 
     */
    private boolean expEnabled;

    private HashMap<Long, ChannelData> channels;

    private BlacklistData blacklistData;

    private HashMap<AlertKey, AlertData> alerts;

    private HashMap<Long, MemberData> users;

    private HashMap<String, CustomCommand> customCommands;

    public GuildData(Long id, String prefix, boolean expSystem) {
        this.ID = id;
        this.prefix = prefix;
        this.expEnabled = expSystem;

        this.users = new HashMap<>();
        retriveChannels();
        retriveCustomCommand();
    }

    private void retriveCustomCommand() {
        customCommands = new HashMap<>();
        /**
         * Da rifare in maniera più ottimizzata ovviamente, era giusto per ottenere
         * i dati subito senza starci troppo a pensare.
         * Anche il codice sotto si può ottimizzare 100x
         */
        QueryResult result = DatabaseHandler.safJQuery("SELECT ID,name,description,slash FROM commands WHERE guild_id = " + ID);
        QueryResult optionResult = DatabaseHandler.safJQuery("SELECT ID,command_id,`key`,description,required,type FROM command_option WHERE command_id IN (SELECT ID FROM commands WHERE guild_id = " + ID + ")");
        QueryResult valueResult = DatabaseHandler.safJQuery("SELECT ID,option_id,`key`,value FROM command_option_value WHERE option_id IN (SELECT ID FROM command_option WHERE command_id IN (SELECT ID FROM commands WHERE guild_id = " + ID + "))");
        QueryResult taskResult = DatabaseHandler.safJQuery("SELECT ID,command_id,type,`order` FROM command_task WHERE command_id IN (SELECT ID FROM commands WHERE guild_id = " + ID + ")");
        QueryResult taskValueResult = DatabaseHandler.safJQuery("SELECT ID,task_id,value,from_option FROM command_task_value WHERE task_id IN (SELECT ID FROM command_task WHERE command_id IN (SELECT ID FROM commands WHERE guild_id = " + ID + "))");
        QueryResult taskMessage = DatabaseHandler.safJQuery("SELECT ID,task_value_id,message FROM command_task_message WHERE task_value_id IN (SELECT ID FROM command_task_value WHERE task_id IN (SELECT ID FROM command_task WHERE command_id IN (SELECT ID FROM commands WHERE guild_id = " + ID + ")))");

        JDA jda = Bot.getJDA();

        for (ResultRow row : result) {
            int id = row.getAsInt("ID");
            String name = row.get("name");
            String description = row.get("description");
            boolean isSlash = row.getAsBoolean("slash");
            CustomCommand cc = new CustomCommand(id, name, description, isSlash);
            for (ResultRow optionRow : optionResult) {
                if (optionRow.getAsInt("command_id") == id) {
                    int optionId = optionRow.getAsInt("ID");
                    String key = optionRow.get("key");
                    String optionDescription = optionRow.get("description");
                    boolean isRequired = optionRow.getAsBoolean("required");
                    OptionType type = OptionType.fromKey(Integer.parseInt(optionRow.get("type")));
                    List<OptionValue> values = new ArrayList<>();
                    for (ResultRow valueRow : valueResult) {
                        if (valueRow.getAsInt("option_id") == optionId) {
                            values.add(new OptionValue(valueRow.getAsInt("ID"), valueRow.get("key"), valueRow.get("value")));
                        }
                    }
                    Option option = new Option(optionId, key, optionDescription, isRequired, type);
                    if (!values.isEmpty()) {
                        option = new Option(optionId, key, optionDescription, isRequired, values);
                    }
                    cc.addOption(option);
                }
            }
            for (ResultRow taskRow : taskResult) {
                if (taskRow.getAsInt("command_id") == id) {
                    int taskId = taskRow.getAsInt("ID");
                    TaskType type = TaskType.fromValue(taskRow.getAsInt("type"));
                    Task task = new Task(taskId, type);
                    for (ResultRow taskValueRow : taskValueResult) {
                        if (taskValueRow.getAsInt("task_id") == taskId) {
                            boolean fromOption = taskValueRow.getAsBoolean("from_option");
                            String value = taskValueRow.get("value");
                            if (fromOption) {
                                value = "#" + value;
                            }
                            if (type == TaskType.SEND_MESSAGE) {
                                for (ResultRow messageRow : taskMessage) {
                                    if (messageRow.getAsInt("task_value_id") == taskValueRow.getAsInt("ID")) {
                                        value = messageRow.get("message");
                                    }
                                }
                            }
                            task.addValue(value);
                        }
                    }
                    cc.addTask(task);
                }
            }
            customCommands.put(name, cc);
        }

        /*
         * g.updateCommands()
             .addCommands(Commands.slash("custom_command", "Gives the current ping")).queue();
         */
        Guild g = jda.getGuildById(ID);
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
                System.out.println(scd.getName());
                commands.add(scd);
            });
        }
        System.out.println("commands size " + commands.size());
        g.updateCommands().addCommands(commands).queue();
    }


    public CustomCommand getCustomCommand(String name) {
        return customCommands.get(name);
    }

    public Long getId() {
        return ID;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isExpSystemEnabled() {
        return expEnabled;
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
            this.expEnabled = expSystem;
        }
        return result;
    }

    public String toString(){
        return "ID: " + ID + " | Prefix: " + prefix + " | ExpSystem: " + expEnabled;
    }


    /* -------------------------------------------------------------------------- */
    /*                                AlertData                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * If the {@link #alerts alerts map} is null, it will be retrieved from the database and cached.
     * <p>
     * The key is the {@link AlertType type} of the alert.
     * </p>
     * <p>
     * If the alert is {@link AlertType#WELCOME WELCOME}, the will be also retrieved the roles, otherwise it will be null.
     * @return
     */
    public HashMap<AlertKey, AlertData> getAlerts() {
        if (this.alerts == null) {
            System.out.println("[CACHE] Retriving AlertData from database => " + ID);
            this.alerts = new HashMap<>();
            QueryResult alertResult = DatabaseHandler.getAlerts(String.valueOf(ID));
            QueryResult roleResult = DatabaseHandler.getAlertsRoles(String.valueOf(ID));
            QueryResult rewardResult = DatabaseHandler.getRewardData(String.valueOf(ID));
            
            HashMap<Integer, HashMap<Integer, String>> roles = new HashMap<>();
            for (ResultRow row : roleResult) {
                int alertId = row.getAsInt("alert_id");
                String roleId = row.get("role_id");
                int rowId = row.getAsInt("row_id");
                if (!roles.containsKey(alertId)) {
                    roles.put(alertId, new HashMap<>());
                }
                roles.get(alertId).put(rowId, roleId);
            }

            HashMap<Integer, ResultRow> rewards = new HashMap<>();
            for (ResultRow row : rewardResult) {
                rewards.put(row.getAsInt("alert_id"), row);
            }

            for (ResultRow row : alertResult) {
                switch (row.getAsInt("type")) {
                    case 4:
                        RewardData rd = new RewardData(
                            row.getAsInt("id"),
                            row.get("message"),
                            row.getAsBoolean("enabled"),
                            row.getAsBoolean("private"),
                            roles.get(row.getAsInt("id")),
                            rewards.get(row.getAsInt("id")).getAsInt("level"),
                            rewards.get(row.getAsInt("id")).getAsBoolean("temporary")
                        );
                        this.alerts.put(rd.getKey(), rd);
                        break;
                
                    default:
                        AlertData ad = new AlertData(
                            row.getAsInt("id"),
                            row.get("message"),
                            row.get("channel"),
                            row.getAsBoolean("enabled"),
                            row.getAsBoolean("private"),
                            AlertType.values()[row.getAsInt("type")],
                            roles.get(row.getAsInt("id"))
                        );
                        this.alerts.put(ad.getKey(), ad);
                    break;
                }
                
            }
        }
        return this.alerts;
    }

    public AlertData getAlert(AlertType type) {
        return getAlerts().get(new AlertKey(type));
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
        AlertData alert = getAlerts().get(new AlertKey(type, level));
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
        AlertData toDelete = getAlerts().remove(new AlertKey(type, level));
        if (toDelete == null) {
            return false;
        }
        return toDelete.terminator4LaRinascita();
    }

    /* -------------------------------------------------------------------------- */
    /*                                BlacklistData                               */
    /* -------------------------------------------------------------------------- */


    public BlacklistData getBlacklistData() {
        if (this.blacklistData == null) {
            BlacklistData bd = null;
            ResultRow result = DatabaseHandler.getGuildData(String.valueOf(ID));
            System.out.println("[CACHE] Retriving BlacklistData from database => " + ID);
            bd = new BlacklistData(
                result.getAsInt("threshold"),
                result.get("blacklist_channel"),
                result.getAsBoolean("blacklist_enabled"),
                this
            );
            this.blacklistData = bd;
        }
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


    /* -------------------------------------------------------------------------- */
    /*                                Channel Data                                */
    /* -------------------------------------------------------------------------- */


    public void retriveChannels() {
        this.channels = new HashMap<>();
        QueryResult result = DatabaseHandler.getChannelData(String.valueOf(ID));
        
        if (result == null) { return; }
        System.out.println("[CACHE] Retriving ChannelData from database => " + ID);
        for(ResultRow row: result){
            this.channels.put(
                row.getAsLong("channel_id"),
                new ChannelData(
                    row.getAsInt("id"),
                    row.getAsLong("channel_id"),
                    row.getAsBoolean("exp_enabled"),
                    row.getAsDouble("exp_modifier"),
                    row.getAsBoolean("stats_enabled"),
                    this
                )
            );
        }

    }

    public HashMap<Long, ChannelData> getChannels() {
        return this.channels;
    }

    public ChannelData getChannelData(long channel_id) {
        ChannelData cd = this.channels.get(channel_id);
        if (cd == null) {
            cd = new ChannelData(channel_id, this);
            System.out.println("[CACHE] Caching local ChannelData => " + ID + " | " + channel_id);
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

    public double getExpValueRoom(Long id){
        return getChannelData(id).getExpValue();
    }


    public synchronized boolean setExpSystemRoom(Long id, boolean exp){
        return getChannelData(id).setExpEnabled(exp);
    }

    public synchronized boolean setExpValueChannel(Long id, double value){
        return getChannelData(id).setExpModifier(value);
    }

    /* -------------------------------------------------------------------------- */
    /*                                UserData                                    */
    /* -------------------------------------------------------------------------- */

    private MemberData retriveUserData(long userId) {
        MemberData ud = null;
        ResultRow result = DatabaseHandler.getUserData(String.valueOf(ID), userId);
        if (result.emptyValues()) { return null; }
        System.out.println("[CACHE] Retriving UserData from database => " + ID + " | " + userId);
        ud = new MemberData(
            result.getAsInt("id"),
            userId,
            result.getAsInt("experience"),
            result.getAsInt("level"),
            result.getAsInt("messages"),
            result.getAsInt("update_time"),
            this
        );
        return ud;
    }

    public MemberData getUserData(String userId) {
        return getUserData(Long.parseLong(userId));
    }

    public MemberData getUserData(long userId) {
        MemberData ud = this.users.get(userId);
        if (ud != null) {
            return ud;
        }
        ud = retriveUserData(userId);
        if (ud == null) {
            ud = new MemberData(userId, this);
            System.out.println("[CACHE] Caching local UserData => " + ID + " | " + userId);
        }
        this.users.put(userId, ud);
        return ud;
    }

    public HashMap<Long, MemberData> getUsers() {
        return this.users;
    }










    public boolean isBlackListCached() {
        return this.blacklistData != null;
    }

    public boolean isAlertsCached() {
        return this.alerts != null;
    }

    
}
