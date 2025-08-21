package com.safjnest.model.guild;



import com.safjnest.sql.BotDB;

public class BlacklistData {
    
    private GuildData guildData;

    private int threshold;
    private String blackChannelId;
    private boolean blacklist_enabled;


    public BlacklistData(GuildData guildData) {
        this.guildData = guildData;

        this.threshold = 0;
        this.blackChannelId = null;
        this.blacklist_enabled = false;
    }

    public BlacklistData(int threshold, String blackChannelId, boolean blacklist_enabled, GuildData guildData) {
        this.guildData = guildData;
        
        this.threshold = threshold;
        this.blackChannelId = blackChannelId;
        this.blacklist_enabled = blacklist_enabled;
    }

    public int getThreshold() {
        return threshold;
    }

    public String getBlackChannelId() {
        return blackChannelId;
    }

    public boolean isBlacklistEnabled() {
        return blacklist_enabled;
    }

    public synchronized boolean setThreshold(int threshold) {
        boolean result = BotDB.setBlacklistThreshold(String.valueOf(threshold), String.valueOf(guildData.getId()));
        if (result) {
            this.threshold = threshold;
        }
        return result;
    }

    public synchronized boolean setBlackChannelId(String blackChannelId) {
        boolean result = BotDB.setBlacklistChannel(blackChannelId, String.valueOf(guildData.getId()));
        if (result) {
            this.blackChannelId = blackChannelId;
        }
        return result;
    }

    public synchronized boolean setBlacklistEnabled(boolean toggle) {
        boolean result = BotDB.toggleBlacklist(String.valueOf(guildData.getId()), toggle);
        if (result) {
            this.blacklist_enabled = toggle;
        }
        return result;
    }

    public boolean update() {
        return BotDB.enableBlacklist(String.valueOf(guildData.getId()), String.valueOf(threshold), blackChannelId);
    }

    @Override
    public String toString() {
        return "Threshold: " + threshold + "| Channel: " + blackChannelId + "| Enabled: " + blacklist_enabled;
    }

}
