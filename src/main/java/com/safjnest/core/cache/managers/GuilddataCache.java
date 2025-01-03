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

/**
 * Class that stores in a {@link GuilddataCache#cache guilds} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class GuilddataCache extends CacheAdapter<String, GuildData> {

    public GuilddataCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(50);
    }
    
    public GuildData getGuild(String id) {
        GuildData guild = super.get(id);
        if(guild == null) {
            guild = retriveGuild(id);
        }
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

    public GuildData retriveGuild(String guildId) {
        BotLogger.info("Retriving guild from database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));
        QueryRecord guildData = DatabaseHandler.getGuildData(guildId);
        
        if(guildData.emptyValues()) {
            return insertGuild(guildId);
        }

        GuildData guild = new GuildData(guildData);
        saveGuild(guild);
        return guild;
    }

    public void retrieveAllGuilds() {
        QueryCollection guilds = DatabaseHandler.getGuildData();
        
        for(QueryRecord guildData : guilds){        
            GuildData guild = new GuildData(guildData);
            saveGuild(guild);
        }
    }

    public GuildData insertGuild(String guildId) {
        DatabaseHandler.insertGuild(guildId, Bot.getPrefix());
        BotLogger.error("Missing guild in database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));

        GuildData guild = new GuildData(Long.parseLong(guildId));
        saveGuild(guild);
        return guild;
    }

    private void saveGuild(GuildData guild) {
        super.put(guild.getID(), guild);
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
