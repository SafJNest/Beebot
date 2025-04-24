package com.safjnest.core.cache.managers;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.safjnest.core.Bot;
import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.Guild;

/**
 * Class that stores in a {@link GuildCache#cache guilds} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class GuildCache extends CacheAdapter<String, GuildData> {

    private static GuildCache instance = new GuildCache();

    public GuildCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(60);
    }

    public static GuildCache getInstance() {
        return instance;
    }

    public static GuildData getGuild(Guild guild) {
        return getGuild(guild.getId());
    }

    public static GuildData getGuild(String id) {
        GuildData guild = instance.get(id);
        if(guild == null) {
            guild = instance.retriveGuild(id);
        }
        return guild;
    }

    public static GuildData getGuildOrPut(Guild guild) {
        return getGuildOrPut(guild.getId());
    }
    
    public static GuildData getGuildOrPut(String id) {
        GuildData guild = instance.get(id);
        if(guild == null) {
            guild = instance.retriveGuild(id);
        }
        if(guild == null) {
            guild = putGuild(id);
        }
        return guild;
    }

    public static GuildData putGuild(String guildId) {
        DatabaseHandler.insertGuild(guildId, Bot.getPrefix());
        BotLogger.error("Missing guild in database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));

        GuildData guild = new GuildData(guildId);
        instance.put(guild);
        return guild;
    }

    /**
     * Born deprecated
     * old getGuildIfCached
     * @Deprecated
     * @param id
     * @return
     */
    public GuildData get(String id) {
        return super.get(id);
    }

    private GuildData retriveGuild(String guildId) {
        BotLogger.info("Retriving guild from database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));
        QueryRecord guildData = DatabaseHandler.getGuildData(guildId);
        
        if(guildData.emptyValues()) {
            return null;
        }

        GuildData guild = new GuildData(guildData);
        put(guild);
        return guild;
    }

    public void retrieveAllGuilds() {
        QueryCollection guilds = DatabaseHandler.getGuildData();
        
        for(QueryRecord guildData : guilds){        
            GuildData guild = new GuildData(guildData);
            put(guild);
        }
    }

    private void put(GuildData guild) {
        super.put(guild.getId(), guild);
    }

    public ConcurrentMap<String, GuildData> getGuilds() {
        return super.asTypedMap();
    }

    /**
     * @deprecated
     */
    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}
}
