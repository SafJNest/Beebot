package com.safjnest.spring.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.safjnest.spring.entity.Guild;
import com.safjnest.spring.repository.GuildRepository;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.model.guild.GuildData;

import java.util.List;
import java.util.Optional;

@Service
public class GuildService {
    
    private final GuildRepository guildRepository;
    
    @Autowired
    public GuildService(GuildRepository guildRepository) {
        this.guildRepository = guildRepository;
    }

    public GuildData getGuildById(String id) {
        QueryRecord guild = BotDB.getGuildData(id);

        if (guild.emptyValues()) {
            return null;
        }

        return new GuildData(guild);
    }
    
    // New JPA-based methods
    public Optional<Guild> findGuildById(String guildId) {
        return guildRepository.findByGuildId(guildId);
    }
    
    public Guild saveGuild(Guild guild) {
        return guildRepository.save(guild);
    }
    
    public Guild createOrUpdateGuild(String guildId, String prefix) {
        Optional<Guild> existingGuild = guildRepository.findByGuildId(guildId);
        if (existingGuild.isPresent()) {
            Guild guild = existingGuild.get();
            guild.setPrefix(prefix);
            return guildRepository.save(guild);
        } else {
            Guild newGuild = new Guild();
            newGuild.setGuildId(guildId);
            newGuild.setPrefix(prefix);
            return guildRepository.save(newGuild);
        }
    }
    
    public boolean updatePrefix(String guildId, String prefix) {
        Optional<Guild> guildOpt = guildRepository.findByGuildId(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            guild.setPrefix(prefix);
            guildRepository.save(guild);
            return true;
        }
        return false;
    }
    
    public boolean toggleLevelUp(String guildId, boolean toggle) {
        Optional<Guild> guildOpt = guildRepository.findByGuildId(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            guild.setExpEnabled(toggle);
            guildRepository.save(guild);
            return true;
        } else {
            Guild newGuild = new Guild();
            newGuild.setGuildId(guildId);
            newGuild.setExpEnabled(toggle);
            guildRepository.save(newGuild);
            return true;
        }
    }
    
    public List<Guild> getGuildsByThreshold(int threshold, String excludeGuildId) {
        return guildRepository.findByThresholdAndBlacklistEnabled(threshold, excludeGuildId);
    }
}
