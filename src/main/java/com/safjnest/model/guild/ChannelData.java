package com.safjnest.model.guild;

import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

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

    private String ID;
    private final String CHANNEL_ID;
    private final String GUILD_ID;
        
    private boolean expEnabled;
    private double experienceModifier;

    private boolean statisticsEnabled;

    private LeagueShard leagueShard;

    public ChannelData(String CHANNEL_ID, String GUILD_ID) {
        this.ID = "0";
        this.CHANNEL_ID = CHANNEL_ID;
        this.GUILD_ID = GUILD_ID;

        this.expEnabled = true;
        this.experienceModifier = 1;

        this.statisticsEnabled = true;

        this.leagueShard = null;
    }
    
    public ChannelData(QueryRecord data) {
        this.ID = data.get("id");
        this.CHANNEL_ID = data.get("channel_id");
        this.GUILD_ID = data.get("guild_id");

        this.expEnabled = data.getAsBoolean("exp_enabled");
        this.experienceModifier = data.getAsDouble("exp_modifier");

        this.statisticsEnabled = data.getAsBoolean("stats_enabled");//TODO: find a better name	

        this.leagueShard = data.getAsInt("league_shard") != 0 ? LeagueShard.values()[data.getAsInt("league_shard")] : null;

    }

    public String getId() {
        return this.ID;
    }

    public String getRoomId() {
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
        
        boolean result = BotDB.setChannelCommandEnabled(this.ID, enabled);
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

        boolean result = BotDB.setChannelExpEnabled(this.ID, exp);
        if (result) {
            this.expEnabled = exp;
        }
        return result;
    }

    public boolean setExperienceModifier(double value) {
        handleEmptyID();

        boolean result = BotDB.setChannelExpModifier(this.ID, value);
        if (result) {
            this.experienceModifier = value;
        }
        return result;
    }

    public boolean setLeagueShard(LeagueShard shard) {
        handleEmptyID();

        boolean result = BotDB.updateShardChannel(ID, shard);
        if (result) {
            this.leagueShard = shard;
        }
        return result;
    }

    public LeagueShard getLeagueShard() {
        return leagueShard;
    }

    public boolean terminator5LaRivolta() {
        if (this.ID.equals("0")) {
            return true;
        }
        return BotDB.deleteChannelData(this.ID);
    }

    private void handleEmptyID() {
        if (this.ID.equals("0")) {
            BotLogger.debug("Pushing local ChannelData into Database => {0}", new LoggerIDpair(String.valueOf(this.CHANNEL_ID), LoggerIDpair.IDType.CHANNEL));
            this.ID = BotDB.insertChannelData(this.GUILD_ID, this.CHANNEL_ID);
        }
    }

    @Override
    public String toString() {
        return "ChannelData [ID=" + ID + ", CHANNEL_ID=" + CHANNEL_ID + ", expEnabled=" + expEnabled + ", experienceModifier="
                + experienceModifier + ", statisticsEnabled=" + statisticsEnabled + "]";
    }

}
