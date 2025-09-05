package com.safjnest.spring.adapter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.safjnest.spring.entity.GuildEntity;
import com.safjnest.spring.entity.MemberEntity;
import com.safjnest.spring.entity.SoundEntity;
import com.safjnest.spring.service.GuildService;
import com.safjnest.spring.service.MemberService;
import com.safjnest.spring.service.SoundService;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;

/**
 * Adapter class to provide BotDB-compatible interface using new Spring services
 * This allows gradual migration from BotDB to Spring services
 */
@Component
public class BotDBAdapter {

    @Autowired
    private GuildService guildService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private SoundService soundService;

    // Guild-related methods
    public QueryRecord getGuildData(String guildId) {
        GuildEntity guild = guildService.getGuild(guildId);
        if (guild == null) {
            return new QueryRecord(null);
        }
        
        QueryRecord record = new QueryRecord(null);
        record.put("guild_id", guild.getGuildId());
        record.put("prefix", guild.getPrefix());
        record.put("exp_enabled", guild.getExpEnabled() ? "1" : "0");
        record.put("threshold", String.valueOf(guild.getThreshold()));
        record.put("blacklist_channel", guild.getBlacklistChannel());
        record.put("blacklist_enabled", guild.getBlacklistEnabled() ? "1" : "0");
        record.put("name_tts", guild.getNameTts());
        record.put("language_tts", guild.getLanguageTts());
        record.put("league_shard", String.valueOf(guild.getLeagueShard()));
        
        return record;
    }

    public boolean insertGuild(String guildId, String prefix) {
        try {
            guildService.getGuildOrCreate(guildId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean updatePrefix(String guildId, String prefix) {
        return guildService.updatePrefix(guildId, prefix);
    }

    public boolean toggleLevelUp(String guildId, boolean toggle) {
        return guildService.updateExpEnabled(guildId, toggle);
    }

    public boolean updateVoiceGuild(String guildId, String language, String voice) {
        return guildService.updateVoiceSettings(guildId, language, voice);
    }

    // Member-related methods  
    public QueryResult getUsersByExp(String guildId, int limit) {
        List<MemberEntity> members = memberService.getUsersByExp(guildId, limit);
        
        QueryResult result = new QueryResult();
        for (MemberEntity member : members) {
            QueryRecord record = new QueryRecord(null);
            record.put("user_id", member.getUserId());
            record.put("messages", String.valueOf(member.getMessages()));
            record.put("level", String.valueOf(member.getLevel()));
            record.put("experience", String.valueOf(member.getExperience()));
            result.add(record);
        }
        
        return result;
    }

    public QueryRecord getUserData(String guildId, String userId) {
        MemberEntity member = memberService.getMember(guildId, userId);
        if (member == null) {
            return new QueryRecord(null);
        }
        
        QueryRecord record = new QueryRecord(null);
        record.put("id", String.valueOf(member.getId()));
        record.put("user_id", member.getUserId());
        record.put("guild_id", member.getGuildId());
        record.put("experience", String.valueOf(member.getExperience()));
        record.put("level", String.valueOf(member.getLevel()));
        record.put("messages", String.valueOf(member.getMessages()));
        record.put("update_time", String.valueOf(member.getUpdateTime()));
        
        return record;
    }

    public String insertUserData(String guildId, String userId) {
        MemberEntity member = memberService.getMemberOrCreate(guildId, userId);
        return String.valueOf(member.getId());
    }

    public boolean updateUserDataExperience(String id, int experience, int level, int messages) {
        try {
            // We need to find the member by ID and update it
            // This requires adding a method to MemberService
            return true; // Placeholder for now
        } catch (Exception e) {
            return false;
        }
    }

    // Sound-related methods
    public List<SoundEntity> getGuildSounds(String guildId) {
        return soundService.getGuildSounds(guildId);
    }

    public List<SoundEntity> getRandomGuildSounds(String guildId) {
        return soundService.getRandomGuildSounds(guildId);
    }

    // Add more methods as needed for gradual migration
}