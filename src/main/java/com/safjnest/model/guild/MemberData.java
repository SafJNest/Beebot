package com.safjnest.model.guild;

import java.time.LocalDateTime;

import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

public class MemberData {
    private String ID;
    private final String USER_ID;
    private final String GUILD_ID;

    private int experience;
    private int level;
    private int messages;
    private int updateTime;
    private LocalDateTime lastMessageTime;

    public MemberData(String USER_ID, String GUILD_ID) {
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
        this.ID = data.get("id");
        this.USER_ID = data.get("user_id");
        this.GUILD_ID = data.get("guild_id");

        this.experience = data.getAsInt("experience");
        this.level = data.getAsInt("level");
        this.messages = data.getAsInt("messages");
        this.updateTime = data.getAsInt("update_time");
        this.lastMessageTime = LocalDateTime.now().minusSeconds(updateTime + 1);
    }

    private void handleEmptyID() {
        if (this.ID.equals("0")) {
            BotLogger.info("Pushing local UserData into Database => {0}", new LoggerIDpair(String.valueOf(this.USER_ID), LoggerIDpair.IDType.USER));
            this.ID = BotDB.insertUserData(this.GUILD_ID, this.USER_ID);
        }
    }

    public String getId() {
        return ID;
    }

    public String getUserId() {
        return USER_ID;
    }

    public int getExperience() {
        return experience;
    }

    public int getLevel() {
        return level;
    }

    public int getMessages() {
        return messages;
    }

    public int getUpdateTime() {
        return updateTime;
    }

    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public boolean canReceiveExperience() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(lastMessageTime.plusSeconds(updateTime))) {
            lastMessageTime = now;
            return true;
        }
        return false;
    }

    public boolean setExpData(int experience, int level) {
        handleEmptyID();
        this.messages++;
        boolean result = BotDB.updateUserDataExperience(this.ID, experience, level, this.messages);

        if (result) {
            this.experience = experience;
            this.level = level;
        }
        else {
            this.messages--;
        }
        return result;

    }

    public boolean setUpdateTime(int updateTime) {
        handleEmptyID();
        boolean result = BotDB.updateUserDataUpdateTime(this.ID, updateTime);
        if (result) {
            this.updateTime = updateTime;
        }
        return result;
    }

    public int warn(String reason) {
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

}
