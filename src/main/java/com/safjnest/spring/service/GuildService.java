package com.safjnest.spring.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.safjnest.core.Bot;
import com.safjnest.spring.entity.GuildEntity;
import com.safjnest.spring.repository.GuildRepository;

@Service
@Transactional
public class GuildService {

    @Autowired
    private GuildRepository guildRepository;

    @Cacheable(value = "guilds", key = "#guildId")
    public GuildEntity getGuild(String guildId) {
        return guildRepository.findByGuildId(guildId).orElse(null);
    }

    @Cacheable(value = "guilds", key = "#guildId")
    public GuildEntity getGuildOrCreate(String guildId) {
        Optional<GuildEntity> guild = guildRepository.findByGuildId(guildId);
        if (guild.isPresent()) {
            return guild.get();
        }
        
        // Create new guild with default prefix
        GuildEntity newGuild = new GuildEntity(guildId, Bot.getPrefix());
        return guildRepository.save(newGuild);
    }

    @CacheEvict(value = "guilds", key = "#guild.guildId")
    public GuildEntity saveGuild(GuildEntity guild) {
        return guildRepository.save(guild);
    }

    @CacheEvict(value = "guilds", key = "#guildId")
    public boolean updatePrefix(String guildId, String prefix) {
        Optional<GuildEntity> guild = guildRepository.findByGuildId(guildId);
        if (guild.isPresent()) {
            GuildEntity entity = guild.get();
            entity.setPrefix(prefix);
            guildRepository.save(entity);
            return true;
        }
        return false;
    }

    @CacheEvict(value = "guilds", key = "#guildId")
    public boolean updateExpEnabled(String guildId, boolean enabled) {
        Optional<GuildEntity> guild = guildRepository.findByGuildId(guildId);
        if (guild.isPresent()) {
            GuildEntity entity = guild.get();
            entity.setExpEnabled(enabled);
            guildRepository.save(entity);
            return true;
        }
        return false;
    }

    @CacheEvict(value = "guilds", key = "#guildId")
    public boolean updateVoiceSettings(String guildId, String language, String voice) {
        Optional<GuildEntity> guild = guildRepository.findByGuildId(guildId);
        if (guild.isPresent()) {
            GuildEntity entity = guild.get();
            entity.setLanguageTts(language);
            entity.setNameTts(voice);
            guildRepository.save(entity);
            return true;
        }
        return false;
    }
}
