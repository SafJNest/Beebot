package com.safjnest.Utilities.Bot;

import com.safjnest.Utilities.Guild.GuildSettings;

import net.dv8tion.jda.api.JDA;

/**
 * This class is used to store the data of a bot and all of its attributes.
 */
public class BotSettings {
    /**
     * The id of the bot.
     */
    public String botId;
    /**
     * The global prefix of the bot.
     */
    public String prefix;
    /**
     * The color of the bot.
     */
    public String color;

    private GuildSettings guildSettings;

    private JDA jda;

    /**
     * Constructor for the BotSettings class.
     * 
     * @param botId The id of the bot.
     * @param prefix The global prefix of the bot.
     * @param color The color of the bot.
     */
    public BotSettings(String botId, String prefix, String color, JDA jda, GuildSettings gs){
        this.botId = botId;
        this.prefix = prefix;
        this.color = color;
        this.jda = jda;
        this.guildSettings = gs;
    }

    public JDA getJda() {
        return this.jda;
    }

    public GuildSettings getGuildSettings() {
        return this.guildSettings;
    }

}
