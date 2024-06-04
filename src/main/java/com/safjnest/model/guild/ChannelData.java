package com.safjnest.model.guild;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

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
    

    private int ID;


    /**
     * ID of the room
     */
    private final long CHANNEL_ID;
        
    /**
     * If the room has the exp system
     * @see com.safjnest.util.ExperienceSystem EXPSystem  
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

    private final String GUILD_ID;
    
    /**
     * Defaul Constructor
     * @param id
     * @param name
     * @param expSystem
     * @param expValue
     * @param command
     */
    public ChannelData(int ID, long CHANNEL_ID, boolean expSystem, double expValue, boolean command, String GUILD_ID) {
        this.ID = ID;
        this.CHANNEL_ID = CHANNEL_ID;
        this.expEnabled = expSystem;
        this.expModifier = expValue;
        this.statisticsEnabled = command;
        this.GUILD_ID = GUILD_ID;
    }

    public ChannelData(long CHANNEL_ID, String GUILD_ID) {
        this.ID = 0;
        this.CHANNEL_ID = CHANNEL_ID;
        this.expEnabled = true;
        this.expModifier = 1;
        this.statisticsEnabled = true;
        this.GUILD_ID = GUILD_ID;
    }

    public int getId() {
        return this.ID;
    }

    public long getRoomId() {
        return this.CHANNEL_ID;
    }

    public boolean isExpSystemEnabled() {
        return expEnabled;
    }

    public boolean getCommand() {
        return statisticsEnabled;
    }

    public boolean setCommand(boolean enabled) {
        handleEmptyID();
        
        boolean result = DatabaseHandler.setChannelCommandEnabled(this.ID, enabled);
        if (result) {
            this.statisticsEnabled = enabled;
        }
        return result;
    }


    public double getExpValue() {
        return expModifier;
    }

    public boolean setExpEnabled(boolean exp) {
        handleEmptyID();

        boolean result = DatabaseHandler.setChannelExpEnabled(this.ID, exp);
        if (result) {
            this.expEnabled = exp;
        }
        return result;
    }

    public boolean setExpModifier(double value) {
        handleEmptyID();

        boolean result = DatabaseHandler.setChannelExpModifier(this.ID, value);
        if (result) {
            this.expModifier = value;
        }
        return result;
    }

    public boolean terminator5LaRivolta() {
        if (this.ID == 0) {
            return true;
        }
        return DatabaseHandler.deleteChannelData(this.ID);
    }

    private void handleEmptyID() {
        if (this.ID == 0) {
            BotLogger.debug("Pushing local ChannelData into Database => {0}", new LoggerIDpair(String.valueOf(this.CHANNEL_ID), LoggerIDpair.IDType.CHANNEL));
            this.ID = DatabaseHandler.insertChannelData(Long.valueOf(this.GUILD_ID), this.CHANNEL_ID);
        }
    }

    @Override
    public String toString() {
        return "ChannelData [ID=" + ID + ", CHANNEL_ID=" + CHANNEL_ID + ", expEnabled=" + expEnabled + ", expModifier="
                + expModifier + ", statisticsEnabled=" + statisticsEnabled + "]";
    }

}
