package com.safjnest.spring.service;

import org.springframework.stereotype.Service;

import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.model.guild.GuildData;

@Service
public class GuildService {

    public GuildData getGuildById(String id) {
        QueryRecord guild = BotDB.getGuildData(id);

        if (guild.emptyValues()) {
            return null;
        }

        return new GuildData(guild);
    }
    
}
