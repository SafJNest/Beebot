package com.safjnest.bot.model.guild;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.safjnest.database.sql.database.BotDB;

public class BlacklistData {

    private final String guildId;
    private final String defaultPrefix;
    private final BotDB database;

    private int threshold;
    private String blackChannelId;
    private boolean blacklist_enabled;

    private BlacklistData original;
    private long revision;

    public BlacklistData(GuildData guild) {
        this(0, null, false, guild);
    }

    public BlacklistData(int threshold, String channelId, boolean enabled, GuildData guild) {
        this.guildId = guild.getId();
        this.defaultPrefix = guild.getPrefix();
        this.database = guild.getDatabase();

        this.threshold = threshold;
        this.blackChannelId = channelId;
        this.blacklist_enabled = enabled;
    }

    private BlacklistData(BlacklistData original) {
        this.guildId = original.guildId;
        this.defaultPrefix = original.defaultPrefix;
        this.database = original.database;

        this.threshold = original.threshold;
        this.blackChannelId = original.blackChannelId;
        this.blacklist_enabled = original.blacklist_enabled;

        this.revision = original.revision;
        this.original = original;
    }

    public synchronized BlacklistData deferEdit() {
        if (original != null)
            throw new IllegalStateException("This blacklist is already an edit");

        return new BlacklistData(this);
    }

    public boolean update() {
        if (original == null)
            return deferEdit().update();

        synchronized (original) {
            if (revision != original.revision)
                return false;

            try {
                database.saveBlacklist(guildId, defaultPrefix, this);
            } catch (IllegalStateException exception) {
                return false;
            }

            original.threshold = threshold;
            original.blackChannelId = blackChannelId;
            original.blacklist_enabled = blacklist_enabled;

            original.revision++;

            return true;
        }
    }

    public synchronized int getThreshold() {
        return threshold;
    }

    public synchronized String getBlackChannelId() {
        return blackChannelId;
    }

    public synchronized boolean isBlacklistEnabled() {
        return blacklist_enabled;
    }

    public boolean setThreshold(int threshold) {
        return change(edit -> edit.threshold = threshold);
    }

    public boolean setBlackChannelId(String channelId) {
        return change(edit -> edit.blackChannelId = channelId);
    }

    public boolean setBlacklistEnabled(boolean enabled) {
        return change(edit -> edit.blacklist_enabled = enabled);
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", blacklist_enabled);
        values.put("threshold", threshold);
        values.put("channelId", blackChannelId);

        return Collections.unmodifiableMap(values);
    }

    @Override
    public synchronized String toString() {
        return "Threshold: " + threshold + "| Channel: " + blackChannelId + "| Enabled: " + blacklist_enabled;
    }

    /* -------------------------------------------------------------------------- */

    private synchronized boolean change(Consumer<BlacklistData> mutation) {
        if (original != null) {
            mutation.accept(this);

            return true;
        }

        BlacklistData edit = deferEdit();
        mutation.accept(edit);

        return edit.update();
    }
}
