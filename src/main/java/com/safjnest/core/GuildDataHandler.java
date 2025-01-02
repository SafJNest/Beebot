package com.safjnest.core;

import java.util.concurrent.TimeUnit;

import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

/**
 * Class that stores in a {@link GuildDataHandler#cache guilds} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class GuildDataHandler extends CacheHandler<String, GuildData> {

    private static final String KEY = "guild_data";
    private static final GuildDataHandler instance = new GuildDataHandler();

    public GuildDataHandler() {
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(2);
    }
    
    public GuildData getGuild(String id) {
        GuildData guild = instance.getInternal(KEY + "-" + id);
        if(guild == null) {
            guild = retriveGuild(id);
        }
        return guild;
    }

    /**
     * Born deprecated
     * @Deprecated
     * @param id
     * @return
     */
    public GuildData getGuildIfCached(String id) {
        return instance.getInternal(KEY + "-" + id);
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
        instance.put(KEY + "-" + guild.getId(), guild);
    }

    public CacheMap<String, GuildData> getGuilds() {
        CacheMap<String, GuildData> users = new CacheMap<>();
        for(String key : instance.asMap(GuildData.class).keySet()) {
            users.put(key, instance.getInternal(key));
        }
        return users;
    }

    /**
     * @deprecated
     */
    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}

    @Override
    protected Class<GuildData> getValueType() {
        return GuildData.class;
    }
}
