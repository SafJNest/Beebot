package com.safjnest.Utilities.Guild;


import java.util.HashMap;


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
    private Long id;
    
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

    private String botId;

    private BlacklistData blacklistData;

    /**
     * 
     * @param id
     * @param prefix
     * @param expSystem
     * @param threshold
     * @param channel
     * @param blacklist_enabled
     */
    public GuildData(Long id, String prefix, boolean expSystem, String botId) {
        this.id = id;
        this.prefix = prefix;
        this.expSystem = expSystem;
        this.botId = botId;
        loadRooms();
    }

    /**
     * Default constructor
     * @param id
     * @param prefix
     */
    public GuildData(Long id, String prefix) {
        this.id = id;
        this.prefix = prefix;
        this.expSystem = false;
        loadRooms();
    }

    public Long getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isExpSystemEnabled() {
        return expSystem;
    }

    public String getBotId() {
        return botId;
    }

    public BlacklistData getBlacklistData() {
        if (this.blacklistData == null) {
            BlacklistData bd = null;
            ResultRow result = DatabaseHandler.getGuildData(String.valueOf(id), botId);
            System.out.println("[CACHE] Retriving BlacklistData from database => " + id);
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
        boolean result = DatabaseHandler.updatePrefix(String.valueOf(id), botId, prefix);
        if (result) {
            this.prefix = prefix;
        }
        return result;
    }

    public synchronized void setExpSystem(boolean expSystem) {
        this.expSystem = expSystem;
    }

    public String toString(){
        return "ID: " + id + "| Prefix: " + prefix + " |ExpSystem: " + expSystem;
    }

    /**
     * Load all the rooms of the guild
     */
    public void loadRooms(){
        rooms = new HashMap<>();
        QueryResult result = DatabaseHandler.getRoomsData(String.valueOf(id));
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
    
}
