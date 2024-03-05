package com.safjnest.Utilities.Bot.Guild;

import com.safjnest.Utilities.SQL.DatabaseHandler;

/**
 * Class that stores all the settings for a guild.
 * <ul>
 * <li>ID</li>
 * <li>expSystem</li>
 * <li>expValue</li>
 * <li>command</li>
 * </ul>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class ChannelData {
    

    private final int ID;


    /**
     * ID of the room
     */
    private final long ROOM_ID;
        
    /**
     * If the room has the exp system
     * @see com.safjnest.Utilities.EXPSystem.ExpSystem EXPSystem  
     */
    private boolean expEnabled;

    /**
     * Value of the exp system
     */
    private double expModifier;

    /**
     * If the room has the command system
     */
    private boolean statisticsEnabled;
    
    /**
     * Defaul Constructor
     * @param id
     * @param name
     * @param expSystem
     * @param expValue
     * @param command
     */
    public ChannelData(int ID, long ROOM_ID, boolean expSystem, double expValue, boolean command) {
        this.ID = ID;
        this.ROOM_ID = ROOM_ID;
        this.expEnabled = expSystem;
        this.expModifier = expValue;
        this.statisticsEnabled = command;
    }

    public int getId() {
        return this.ID;
    }

    public long getRoomId() {
        return this.ROOM_ID;
    }

    public boolean isExpSystemEnabled() {
        return expEnabled;
    }

    public boolean getCommand() {
        return statisticsEnabled;
    }


    public double getExpValue() {
        return expModifier;
    }

    public boolean setExpEnabled(boolean exp) {
        boolean result = DatabaseHandler.setChannelExpEnabled(this.ID, exp);
        if (result) {
            this.expEnabled = exp;
        }
        return result;
    }

    public boolean setExpModifier(double value) {
        boolean result = DatabaseHandler.setChannelExpModifier(this.ID, value);
        if (result) {
            this.expModifier = value;
        }
        return result;
    }

}
