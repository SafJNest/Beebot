package com.safjnest.bot.model.guild;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;

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

    private final String defaultPrefix;
    private final BotDB database;

    private boolean expEnabled;
    private double experienceModifier;

    private boolean statisticsEnabled;

    private LeagueShard leagueShard;

    private ChannelData original;
    private boolean deleted;
    private long revision;

    public ChannelData(String channelId, String guildId) {
        this(channelId, guildId, "!", BotDB.get());
    }

    ChannelData(String channelId, String guildId, String defaultPrefix, BotDB database) {
        this.ID = "0";
        this.CHANNEL_ID = channelId;
        this.GUILD_ID = guildId;

        this.defaultPrefix = defaultPrefix;
        this.database = database;

        this.expEnabled = true;
        this.experienceModifier = 1;

        this.statisticsEnabled = true;
    }

    public ChannelData(QueryRecord row) {
        this(row, BotDB.get());
    }

    ChannelData(QueryRecord row, BotDB database) {
        this.ID = row.get("id");
        this.CHANNEL_ID = row.get("channel_id");
        this.GUILD_ID = row.get("guild_id");

        this.defaultPrefix = "!";
        this.database = database;

        this.expEnabled = row.getAsBoolean("exp_enabled");
        this.experienceModifier = row.getAsDouble("exp_modifier");

        this.statisticsEnabled = row.getAsBoolean("stats_enabled");

        this.leagueShard = row.getAsInt("league_shard") == 0 ? null : LeagueShard.values()[row.getAsInt("league_shard")];
    }

    private ChannelData(ChannelData original) {
        this.ID = original.ID;
        this.CHANNEL_ID = original.CHANNEL_ID;
        this.GUILD_ID = original.GUILD_ID;

        this.defaultPrefix = original.defaultPrefix;
        this.database = original.database;

        this.expEnabled = original.expEnabled;
        this.experienceModifier = original.experienceModifier;

        this.statisticsEnabled = original.statisticsEnabled;

        this.leagueShard = original.leagueShard;

        this.revision = original.revision;
        this.original = original;
    }

    public synchronized ChannelData deferEdit() {
        if (original != null)
            throw new IllegalStateException("This channel is already an edit");

        return new ChannelData(this);
    }

    public boolean update() {
        if (original == null)
            return deferEdit().update();

        synchronized (original) {
            if (original.deleted || revision != original.revision)
                return false;

            int id;

            try {
                id = database.saveChannel(GUILD_ID, defaultPrefix, this);
            } catch (IllegalStateException exception) {
                return false;
            }

            original.ID = String.valueOf(id);

            original.expEnabled = expEnabled;
            original.experienceModifier = experienceModifier;

            original.statisticsEnabled = statisticsEnabled;

            original.leagueShard = leagueShard;

            original.revision++;

            return true;
        }
    }

    public synchronized String getId() {
        return ID;
    }

    public String getRoomId() {
        return CHANNEL_ID;
    }

    public synchronized boolean isExpSystemEnabled() {
        return expEnabled;
    }

    public synchronized boolean getCommand() {
        return statisticsEnabled;
    }

    public boolean setCommand(boolean enabled) {
        return change(edit -> edit.statisticsEnabled = enabled);
    }

    public synchronized double getExperienceModifier() {
        return experienceModifier;
    }

    public boolean enableExperience(boolean enabled) {
        return change(edit -> edit.expEnabled = enabled);
    }

    public boolean setExperienceModifier(double value) {
        return change(edit -> edit.experienceModifier = value);
    }

    public boolean setLeagueShard(LeagueShard shard) {
        return change(edit -> edit.leagueShard = shard);
    }

    public synchronized LeagueShard getLeagueShard() {
        return leagueShard;
    }

    public synchronized boolean terminator5LaRivolta() {
        if (original != null || deleted)
            return false;

        try {
            if (!ID.equals("0") && !database.deleteGuildChannel(GUILD_ID, CHANNEL_ID, defaultPrefix))
                return false;
        } catch (IllegalStateException exception) {
            return false;
        }

        deleted = true;
        revision++;

        return true;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("experienceEnabled", expEnabled);
        values.put("experienceModifier", experienceModifier);
        values.put("statisticsEnabled", statisticsEnabled);
        values.put("leagueShard", leagueShard == null ? null : leagueShard.name());

        return Collections.unmodifiableMap(values);
    }

    @Override
    public synchronized String toString() {
        return "ChannelData [ID=" + ID + ", CHANNEL_ID=" + CHANNEL_ID + ", expEnabled=" + expEnabled + ", experienceModifier="
            + experienceModifier + ", statisticsEnabled=" + statisticsEnabled + "]";
    }

    /* -------------------------------------------------------------------------- */

    private synchronized boolean change(Consumer<ChannelData> mutation) {
        if (original != null) {
            mutation.accept(this);

            return true;
        }

        ChannelData edit = deferEdit();
        mutation.accept(edit);

        return edit.update();
    }
}
