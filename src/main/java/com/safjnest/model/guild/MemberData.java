package com.safjnest.model.guild;

import java.time.LocalDateTime;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

public class MemberData {
    private int ID;
    private final long USER_ID;
    private int experience;
    private int level;
    private int messages;
    private int updateTime;
    private LocalDateTime lastMessageTime;
    private final String GUILD_ID;

    public MemberData(int ID, long USER_ID, int experience, int level, int messages, int updateTime, String GUILD_ID) {
        this.ID = ID;
        this.USER_ID = USER_ID;
        this.experience = experience;
        this.level = level;
        this.messages = messages;
        this.updateTime = updateTime;
        this.lastMessageTime = LocalDateTime.now().minusSeconds(updateTime + 1);
        this.GUILD_ID = GUILD_ID;
    }

    public MemberData(long USER_ID, String GUILD_ID) {
        this.ID = 0;
        this.USER_ID = USER_ID;
        this.experience = 0;
        this.level = 1;
        this.messages = 0;
        this.updateTime = 60;
        this.lastMessageTime = LocalDateTime.now().minusSeconds(updateTime + 1);
        this.GUILD_ID = GUILD_ID;
    }

    private void handleEmptyID() {
        if (this.ID == 0) {
            BotLogger.info("Pushing local UserData into Database => {0}", new LoggerIDpair(String.valueOf(this.USER_ID), LoggerIDpair.IDType.USER));
            this.ID = DatabaseHandler.insertUserData(Long.valueOf(this.GUILD_ID), this.USER_ID);
        }
    }

    public int getID() {
        return ID;
    }

    public long getUserId() {
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
        boolean result = DatabaseHandler.updateUserDataExperience(this.ID, experience, level, this.messages);

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
        boolean result = DatabaseHandler.updateUserDataUpdateTime(this.ID, updateTime);
        if (result) {
            this.updateTime = updateTime;
        }
        return result;
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
