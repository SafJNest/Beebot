package com.safjnest.Utilities.Bot.Guild;



import java.util.HashMap;

import com.safjnest.Utilities.Bot.Guild.Alert.AlertData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertType;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;


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

    private final String BOT_ID;
    
    /**
     * Prefix Server 
     */
    private String prefix;
    
    /**
     * Exp System 
     */
    private boolean expSystem;

    /**
     * A map with all the settings for rooms
     */
    private HashMap<Long, Room> rooms;

    private HashMap<Integer, ChannelData> channels;

    private BlacklistData blacklistData;

    private HashMap<AlertType, AlertData> alerts;

    public GuildData(Long id, String prefix, boolean expSystem, String botId) {
        this.ID = id;
        this.prefix = prefix;
        this.expSystem = expSystem;
        this.BOT_ID = botId;
        loadRooms();
        retriveChannels();
    }

    public Long getId() {
        return ID;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isExpSystemEnabled() {
        return expSystem;
    }

    public String getBotId() {
        return BOT_ID;
    }

    /**
     * If the {@link #alerts alerts map} is null, it will be retrieved from the database and cached.
     * <p>
     * The key is the {@link AlertType type} of the alert.
     * </p>
     * <p>
     * If the alert is {@link AlertType#WELCOME WELCOME}, the will be also retrieved the roles, otherwise it will be null.
     * @return
     */
    public HashMap<AlertType, AlertData> getAlerts() {
        if (this.alerts == null) {
            System.out.println("[CACHE] Retriving AlertData from database => " + ID);
            this.alerts = new HashMap<>();
            QueryResult alertResult = DatabaseHandler.getAlerts(String.valueOf(ID), BOT_ID);
            QueryResult roleResult = DatabaseHandler.getAlertsRoles(String.valueOf(ID), BOT_ID);
            
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

            for (ResultRow row : alertResult) {
                AlertData ad = new AlertData(
                    row.getAsInt("id"),
                    row.get("message"),
                    row.get("channel"),
                    row.getAsBoolean("enabled"),
                    AlertType.values()[row.getAsInt("type")],
                    roles.get(row.getAsInt("id"))
                );
                this.alerts.put(ad.getType(), ad);
            }
        }
        return this.alerts;
    }

    public AlertData getAlert(AlertType type) {
        return getAlerts().get(type);
    }

    public AlertData getWelcome() {
        return getAlert(AlertType.WELCOME);
    }

    public boolean deleteAlert(AlertType type) {
        AlertData toDelete = getAlerts().remove(type);
        if (toDelete == null) {
            return false;
        }
        return toDelete.terminator4LaRinascita();
    }

    public BlacklistData getBlacklistData() {
        if (this.blacklistData == null) {
            BlacklistData bd = null;
            ResultRow result = DatabaseHandler.getGuildData(String.valueOf(ID), BOT_ID);
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

    public synchronized boolean setPrefix(String prefix) {
        boolean result = DatabaseHandler.updatePrefix(String.valueOf(ID), BOT_ID, prefix);
        if (result) {
            this.prefix = prefix;
        }
        return result;
    }

    public synchronized boolean setExpSystem(boolean expSystem) {
        boolean result = DatabaseHandler.toggleLevelUp(String.valueOf(this.ID), this.BOT_ID, expSystem);
        if (result) {
            this.expSystem = expSystem;
        }
        return result;
    }

    public String toString(){
        return "ID: " + ID + "| Prefix: " + prefix + " |ExpSystem: " + expSystem;
    }

    /**
     * Load all the rooms of the guild
     */
    public void loadRooms(){
        rooms = new HashMap<>();
        QueryResult result = DatabaseHandler.getRoomsData(String.valueOf(ID));
        for(ResultRow row: result){;
            rooms.put(
                row.getAsLong("room_id"),
                new Room(
                    row.getAsLong("room_id"), 
                    row.getAsBoolean("has_exp"), 
                    row.getAsDouble("exp_value"),
                    row.getAsBoolean("has_command_stats")
                    )
            );
        }
    }

    public void retriveChannels() {
        this.channels = new HashMap<>();
        QueryResult result = DatabaseHandler.getChannelData(String.valueOf(ID), BOT_ID);
        
        if (result == null) { return; }

        for(ResultRow row: result){
            this.channels.put(
                row.getAsInt("id"),
                new ChannelData(
                    row.getAsInt("id"),
                    row.getAsLong("room_id"),
                    row.getAsBoolean("exp_enabled"),
                    row.getAsDouble("exp_value"),
                    row.getAsBoolean("command_stats")
                )
            );
        }

    }


    public Room getRoom(Long id){
        return rooms.get(id);
    }

    public Boolean getExpSystemRoom(Long id){
        try {
            return rooms.get(id).isExpSystemEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    public Boolean getCommandStatsRoom(Long id){
        try {
            return rooms.get(id).getCommand();
        } catch (Exception e) {
            return true;
        }
    }

    public double getExpValueRoom(Long id){
        try {
            return rooms.get(id).getExpValue();
        } catch (Exception e) {
            return 1;
        }
    }

    public synchronized void addRoom(Room room){
        rooms.put(room.getId(), room);
    }

    public synchronized void setExpSystemRoom(Long id, boolean exp){
        rooms.get(id).setExpSystem(exp);
    }

    public synchronized void setExpValueRoom(Long id, double value){
        rooms.get(id).setExpValue(value);
    }

    public boolean blacklistEnabled() {
        return getBlacklistData().isBlacklistEnabled();
    }
    
    public boolean setBlacklistEnabled(boolean blacklist_enabled) {
        return getBlacklistData().setBlacklistEnabled(blacklist_enabled);
    }

    public boolean isBlackListCached() {
        return this.blacklistData != null;
    }

    public boolean isAlertsCached() {
        return this.alerts != null;
    }
    
}
