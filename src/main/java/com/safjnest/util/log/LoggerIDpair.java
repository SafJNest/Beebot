package com.safjnest.util.log;

import com.safjnest.core.Bot;

public class LoggerIDpair {
    public enum IDType {
        USER,
        GUILD,
        CHANNEL
    }


    private final String ID;
    private final IDType type;

    public LoggerIDpair(String ID, IDType type) {
        this.ID = ID;
        this.type = type;
    }

    public String getID() {
        return ID;
    }

    public IDType getType() {
        return type;
    }

    public String format() {
        String name = "";
        switch (type) {
            case USER:
                name = Bot.getJDA().getUserById(ID) != null ? Bot.getJDA().getUserById(ID).getName() : "Unknow";
                break;
            case GUILD:
                name = Bot.getJDA().getGuildById(ID) != null ? Bot.getJDA().getGuildById(ID).getName() : "Unknow";
                break;
            case CHANNEL:
                name = Bot.getJDA().getTextChannelById(ID) != null ? Bot.getJDA().getTextChannelById(ID).getName() : "Unknow";
                break;
        }
        return name + " (" + ID + ")";
    }

    @Override
    public String toString() {
        return "LoggerIDpair{" +
                "ID='" + ID + '\'' +
                ", type=" + type +
                '}';
    }

}
