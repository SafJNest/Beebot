package com.safjnest.Utilities.Bot;

import java.util.HashMap;

import org.springframework.stereotype.Component;




/**
 * This class is used to store the data of all the bots and all of their attributes.
 * <p>This is usually setupped in the main class of the bot during the startup 
 * and the attributes are loaded in the {@link com.safjnest.Bot bot} class.</p>
 */
@Component
public class BotDataHandler {
    
    /**
     * The map of all the bots and their settings.
     * <p>The key is the id of the bot and the value is the {@link com.safjnest.Utilities.Bot.BotData BotSettings} object.</p>
     */
    public static HashMap<String, BotData> map;

    /**
     * Constructor for the BotSettingsHandler class.
     */
    public BotDataHandler(){
        map = new HashMap<String, BotData>();
    }


    /**
     * Returns the settings of a bot.
     * 
     * @param botId The id of the bot.
     * @return The settings of the bot.
     */
    public synchronized void setSettings(BotData bs, String botId){
        map.put(botId, bs);
    }

    public static BotData getSettings(String botId){
        return map.get(botId);
    }

    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}

}
