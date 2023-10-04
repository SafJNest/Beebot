package com.safjnest.Utilities.Guild;

import java.util.ArrayList;
import java.util.HashMap;

import com.safjnest.Utilities.SQL.DatabaseHandler;



/**
 * Class that stores in a {@link GuildSettings#cache cache} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class GuildSettings {
    /**
     * {@code HashMap} that contains all the {@link com.safjnest.Utilities.Guild.GuildData settings} of every guild.
     * <p>The key of the map is the guild's id.
     */
    public HashMap<String, GuildData> cache = new HashMap<>();
    private String botId;
    private final String PREFIX;
    final GuildData data;

    /**
     * Default constructor
     * @param input
     */
    public GuildSettings(GuildData input, String botId, String PREFIX) {
        data = input;
        this.botId = botId;
        this.PREFIX = PREFIX;
    }

    /**
     * This method checks if a guild is in the cache, otherwise will be called {@link GuildSettings#retrieveServer() retrievServer}
     * to search for it in the {@link com.safjnest.Utilities.SQL.SQL mysql database}.
     * @param id Server ID
     * @return
     * The {@link com.safjnest.Utilities.Guild.GuildData guildData} if is stored in the cache(or is in the database), otherwise a defult {@link com.safjnest.Utilities.Guild.GuildData guildData}.
     * @see {@link com.safjnest.Utilities.Guild.GuildData guildData and default guildData}
     */
    public GuildData getServer(String id) {
        if(cache.containsKey(id)) 
            return cache.get(id);
         else 
            return retrieveServer(id);
         
        
    }

    /**
     * Born deprecated
     * @Deprecated
     * @param id
     * @return
     */
    public GuildData getServerIfCached(String id) {
        return cache.get(id);
    }

    /**
     * Search the gived guild in the {@link com.safjnest.Utilities.SQL.SQL postgre database}.
     * If the query found it all the settings will be downloaded and saved in the cache, otherwise will be used
     * the default settings:
     * <ul>
     * <li>Guild ID</li>
     * <li>Default PREFIX, depends on the bot ($, %, P)</li>
     * </ul>
     * @param stringId guild's ID
     * @return
     * Always a {@link com.safjnest.Utilities.Guild.GuildData guildData}, never {@code null}
     */
    public GuildData retrieveServer(String stringId) {
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE guild_id = '" + stringId + "' AND bot_id = '" + botId + "';";
        System.out.println("[CACHE] Retriving guild from database => " + stringId);
        ArrayList<String> guildArrayList = DatabaseHandler.getSql().getSpecifiedRow(query, 0);
        
        if(guildArrayList == null) {
            return insertGuild(stringId);
        }

        Long guildId = Long.parseLong(guildArrayList.get(0));
        String PREFIX = guildArrayList.get(1);
        boolean expEnabled = (guildArrayList.get(2).equals("1"));
        int threshold = Integer.parseInt(guildArrayList.get(3));
        String blacklistChannel = guildArrayList.get(4);

        GuildData guild = new GuildData(guildId, PREFIX, expEnabled, threshold, blacklistChannel);
        saveData(guild);
        return guild;
    }

    /**
     * Search the gived guild in the {@link com.safjnest.Utilities.SQL.SQL postgre database}.
     * If the query found it all the settings will be downloaded and saved in the cache, otherwise will be used
     * the default settings:
     * <ul>
     * <li>Guild ID</li>
     * <li>Default PREFIX, depends on the bot ($, %, P)</li>
     * </ul>
     * @param stringId guild's ID
     * @return
     * Always a {@link com.safjnest.Utilities.Guild.GuildData guildData}, never {@code null}
     */
    public void retrieveAllServers() {
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE bot_id = '" + botId + "';";
        ArrayList<ArrayList<String>> guildsArrayList = DatabaseHandler.getSql().getAllRows(query, 5);
        
        for(ArrayList<String> guildArrayList : guildsArrayList){
            Long guildId = Long.parseLong(guildArrayList.get(0));
            String PREFIX = guildArrayList.get(1);
            boolean expEnabled = (guildArrayList.get(2).equals("1"));
            int threshold = Integer.parseInt(guildArrayList.get(3));
            String blacklistChannel = guildArrayList.get(4);
    
            GuildData guild = new GuildData(guildId, PREFIX, expEnabled, threshold, blacklistChannel);
            saveData(guild);
        }
    }

    public GuildData insertGuild(String guildId) {
        String query = "INSERT INTO guild_settings (guild_id, bot_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guildId + "', '" + botId + "', '" + PREFIX + "', '0', '0', null);";
        System.out.println("[ERROR] Missing guild in database => " + query);

        DatabaseHandler.getSql().runQuery(query);
        GuildData guild = new GuildData(Long.parseLong(guildId), PREFIX, false, 0, null);
        saveData(guild);
        return guild;
    }

    /**
     * Saves in the {@link GuildSettings#cache cache} the {@link com.safjnest.Utilities.Guild.GuildData guildData}
     * @param guild guildData
     */
    public void saveData(GuildData guild) {
        cache.put(String.valueOf(guild.getId()), guild);
    }

    public String getId() {
        return data.getId().toString();
    }

    public String getPrefix() {
        return data.getPrefix();
    }

    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}
}
