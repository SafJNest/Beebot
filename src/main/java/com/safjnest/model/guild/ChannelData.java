package com.safjnest.model.guild;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
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
    private final long CHANNEL_ID;
    private final String GUILD_ID;
        
    private boolean expEnabled;
    private double experienceModifier;

    private boolean statisticsEnabled;

    public ChannelData(long CHANNEL_ID, String GUILD_ID) {
        this.ID = 0;
        this.CHANNEL_ID = CHANNEL_ID;
        this.GUILD_ID = GUILD_ID;

        this.expEnabled = true;
        this.experienceModifier = 1;

        this.statisticsEnabled = true;
    }
    
    public ChannelData(ResultRow data) {
        this.ID = data.getAsInt("id");
        this.CHANNEL_ID = data.getAsLong("channel_id");
        this.GUILD_ID = data.get("guild_id");

        this.expEnabled = data.getAsBoolean("exp_enabled");
        this.experienceModifier = data.getAsDouble("exp_modifier");

        this.statisticsEnabled = data.getAsBoolean("command_enabled");
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


    public double getExperienceModifier() {
        return experienceModifier;
    }

    public boolean enableExperience(boolean exp) {
        handleEmptyID();

        boolean result = DatabaseHandler.setChannelExpEnabled(this.ID, exp);
        if (result) {
            this.expEnabled = exp;
        }
        return result;
    }

    public boolean setExperienceModifier(double value) {
        handleEmptyID();

        boolean result = DatabaseHandler.setChannelExpModifier(this.ID, value);
        if (result) {
            this.experienceModifier = value;
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
        return "ChannelData [ID=" + ID + ", CHANNEL_ID=" + CHANNEL_ID + ", expEnabled=" + expEnabled + ", experienceModifier="
                + experienceModifier + ", statisticsEnabled=" + statisticsEnabled + "]";
    }

}
