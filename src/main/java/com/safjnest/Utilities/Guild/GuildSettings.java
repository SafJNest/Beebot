package com.safjnest.Utilities.Guild;

import java.util.HashMap;

import com.safjnest.Bot;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;



/**
 * Class that stores in a {@link GuildSettings#cache guilds} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class GuildSettings {
    /**
     * {@code HashMap} that contains all the {@link com.safjnest.Utilities.Guild.GuildData settings} of every guild.
     * <p>The key of the map is the guild's id.
     */
    private HashMap<String, GuildData> guilds = new HashMap<>();

    /**
     * Default constructor
     * @param input
     */
    public GuildSettings() { }
    

    /**
     * This method checks if a guild is in the cache, otherwise will be called {@link GuildSettings#retrieveServer() retrievServer}
     * to search for it in the {@link com.safjnest.Utilities.SQL.SQL mysql database}.
     * @param id Server ID
     * @return
     * The {@link com.safjnest.Utilities.Guild.GuildData guildData} if is stored in the cache(or is in the database), otherwise a defult {@link com.safjnest.Utilities.Guild.GuildData guildData}.
     * @see {@link com.safjnest.Utilities.Guild.GuildData guildData and default guildData}
     */
    public GuildData getGuild(String id) {
        return guilds.containsKey(id) ? guilds.get(id) : retriveGuild(id);
    }

    /**
     * Born deprecated
     * @Deprecated
     * @param id
     * @return
     */
    public GuildData getGuildIfCached(String id) {
        return guilds.get(id);
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
    public GuildData retriveGuild(String stringId) {
        System.out.println("[CACHE] Retriving guild from database => " + stringId);
        ResultRow guildData = DatabaseHandler.getGuildData(stringId);
        
        if(guildData.emptyValues()) {
            return insertGuild(stringId);
        }

        Long guildId = guildData.getAsLong("guild_id");
        String prefix = guildData.get("prefix");
        boolean expEnabled = guildData.getAsBoolean("exp_enabled");
        LeagueShard shard = LeagueShard.values()[Integer.parseInt(guildData.get("league_shard"))];
        
        GuildData guild = new GuildData(guildId, prefix, expEnabled, shard);
        saveGuild(guild);
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
    public void retrieveAllGuilds() {
        QueryResult guilds = DatabaseHandler.getGuildData();
        
        for(ResultRow guildData : guilds){
            Long guildId = guildData.getAsLong("guild_id");
            String prefix = guildData.get("prefix");
            boolean expEnabled = guildData.getAsBoolean("exp_enabled");
            LeagueShard shard = LeagueShard.valueOf(guildData.get("league_shard"));
        
            GuildData guild = new GuildData(guildId, prefix, expEnabled, shard);
            saveGuild(guild);
        }
    }

    public GuildData insertGuild(String guildId) {
        DatabaseHandler.insertGuild(guildId, Bot.getPrefix());
        System.out.println("[ERROR] Missing guild in database => " + guildId);

        GuildData guild = new GuildData(Long.parseLong(guildId), Bot.getPrefix(), true, LeagueShard.EUW1);
        saveGuild(guild);
        return guild;
    }

    /**
     * Saves in the {@link GuildSettings#cache cache} the {@link com.safjnest.Utilities.Guild.GuildData guildData}
     * @param guild guildData
     */
    private void saveGuild(GuildData guild) {
        guilds.put(String.valueOf(guild.getId()), guild);
    }

    public HashMap<String, GuildData> getGuilds() {
        return guilds;
    }

    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}
}
