package com.safjnest.bot.model.guild;

import java.time.LocalDateTime;

import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.utils.ExperienceSystem;

public class MemberData {
    private String ID;
    private final String USER_ID;
    private final String GUILD_ID;

    private final BotDB database;
    private final String defaultPrefix;

    private int experience;
    private int level;

    private int messages;

    private int updateTime;
    private LocalDateTime lastMessageTime;

    public MemberData(String USER_ID, String GUILD_ID) {
        this(USER_ID, GUILD_ID, "!", BotDB.get());
    }

    public MemberData(String USER_ID, String GUILD_ID, String defaultPrefix, BotDB database) {
        this.database = database;
        this.defaultPrefix = defaultPrefix;

        this.ID = "0";
        this.USER_ID = USER_ID;
        this.GUILD_ID = GUILD_ID;

        this.experience = 0;
        this.level = 1;

        this.messages = 0;

        this.updateTime = 60;
        this.lastMessageTime = LocalDateTime.now().minusSeconds(updateTime + 1);
    }

    public MemberData(QueryRecord data) {
        this(data, "!", BotDB.get());
    }

    public MemberData(QueryRecord data, String defaultPrefix, BotDB database) {
        this.database = database;
        this.defaultPrefix = defaultPrefix;

        this.ID = data.get("id");
        this.USER_ID = data.get("user_id");
        this.GUILD_ID = data.get("guild_id");

        this.experience = data.getAsInt("experience");
        this.level = data.getAsInt("level");

        this.messages = data.getAsInt("messages");

        this.updateTime = data.getAsInt("update_time");
        this.lastMessageTime = LocalDateTime.now().minusSeconds(updateTime + 1);
    }

    public synchronized String getId() {
        return ID;
    }

    public String getUserId() {
        return USER_ID;
    }

    public synchronized int getExperience() {
        return experience;
    }

    public synchronized int getLevel() {
        return level;
    }

    public synchronized int getMessages() {
        return messages;
    }

    public synchronized int getUpdateTime() {
        return updateTime;
    }

    public synchronized LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public synchronized boolean canReceiveExperience() {
        return LocalDateTime.now().isAfter(lastMessageTime.plusSeconds(updateTime));
    }

    public record Experience(boolean saved, boolean leveledUp, int level) {}

    public synchronized Experience receiveExperience(double modifier) {
        LocalDateTime now = LocalDateTime.now();
        if (!now.isAfter(lastMessageTime.plusSeconds(updateTime)))
            return new Experience(false, false, level);

        int nextExperience = ExperienceSystem.calculateExp(experience, modifier);
        int calculatedLevel = ExperienceSystem.isLevelUp(nextExperience, level);
        int nextLevel = calculatedLevel == ExperienceSystem.NOT_LEVELED_UP ? level : calculatedLevel;

        if (!setExpData(nextExperience, nextLevel))
            return new Experience(false, false, level);

        lastMessageTime = now;

        return new Experience(true, calculatedLevel != ExperienceSystem.NOT_LEVELED_UP, level);
    }

    public synchronized boolean setExpData(int experience, int level) {
        try {
            String id = database.saveMember(GUILD_ID, USER_ID, defaultPrefix,
                experience, level, messages + 1, updateTime);

            this.ID = id;
            this.experience = experience;
            this.level = level;
            this.messages++;

            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public synchronized boolean setUpdateTime(int updateTime) {
        try {
            String id = database.saveMember(GUILD_ID, USER_ID, defaultPrefix,
                experience, level, messages, updateTime);

            this.ID = id;
            this.updateTime = updateTime;

            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public synchronized int warn(String reason) {
        handleEmptyID();
        return BotDB.insertWarn(this.ID, reason);
    }

    @Override
    public String toString() {
        return "UserData{" +
                "ID=" + ID +
                ", USER_ID=" + USER_ID +
                ", experience=" + experience +
                ", level=" + level +
                ", messages=" + messages +
                ", updateTime=" + updateTime +
                ", lastMessageTime=" + lastMessageTime +
                '}';
    }

    /* -------------------------------------------------------------------------- */

    private void handleEmptyID() {
        if (ID.equals("0"))
            ID = database.saveMember(GUILD_ID, USER_ID, defaultPrefix, experience, level, messages, updateTime);
    }


}
