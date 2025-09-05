package com.safjnest.core.cache.managers;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.safjnest.core.Bot;
import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.guild.GuildData;
import com.safjnest.spring.entity.GuildEntity;
import com.safjnest.spring.service.GuildService;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.Guild;

/**
 * Class that stores in a {@link GuildCache#cache guilds} all the settings for a guild.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
@Component
public class GuildCache extends CacheAdapter<String, GuildData> {

    private static GuildCache instance;
    
    @Autowired
    private GuildService guildService;

    public GuildCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(60);
        instance = this;
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
            guild = instance.retrieveGuild(id);
        }
        return guild;
    }

    public static GuildData getGuildOrPut(Guild guild) {
        return getGuildOrPut(guild.getId());
    }
    
    public static GuildData getGuildOrPut(String id) {
        GuildData guild = instance.get(id);
        if(guild == null) {
            guild = instance.retrieveGuild(id);
        }
        if(guild == null) {
            guild = putGuild(id);
        }
        return guild;
    }

    public static GuildData putGuild(String guildId) {
        // Use GuildService to create or get guild
        GuildEntity entity = instance.guildService.getGuildOrCreate(guildId);
        
        BotLogger.error("Missing guild in database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));

        GuildData guild = convertToGuildData(entity);
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

    private GuildData retrieveGuild(String guildId) {
        BotLogger.info("Retrieving guild from database => {0}", new LoggerIDpair(guildId, LoggerIDpair.IDType.GUILD));
        
        GuildEntity guildEntity = guildService.getGuild(guildId);
        
        if(guildEntity == null) {
            return null;
        }

        GuildData guild = convertToGuildData(guildEntity);
        put(guild);
        return guild;
    }

    private static GuildData convertToGuildData(GuildEntity entity) {
        // Convert GuildEntity to GuildData - this will require updating GuildData constructor
        // For now, we'll need to adapt based on what GuildData expects
        GuildData guildData = new GuildData(entity.getGuildId());
        // Set other properties as needed based on GuildData structure
        return guildData;
    }

    public void retrieveAllGuilds() {
        // This method would need to be updated to work with the service layer
        // For now, we'll leave it as a placeholder
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
