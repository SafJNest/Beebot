package com.safjnest;

public class BotSettings {
    public String botId;
    public String prefix;
    public String color;

    public BotSettings(String botId, String prefix, String color){
        this.botId = botId;
        this.prefix = prefix;
        this.color = color;
        System.out.println(color + prefix + botId);
    }
}
