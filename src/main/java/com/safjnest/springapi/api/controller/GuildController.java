package com.safjnest.springapi.api.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.springapi.service.GuildService;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

@RestController
@RequestMapping("/api/guild")
public class GuildController {

    private GuildService guildService;

    @Autowired
    public GuildController(GuildService guildService) {
        this.guildService = guildService;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    @GetMapping("/{id}")
    public GuildData getGuild(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing guild id");
        }

        GuildData guild = guildService.getGuildById(id);
        if (guild == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guild not found");
        }
        
        return guild;
    }

    @PostMapping("/{id}/prefix")
    public String setPrefix(@PathVariable String id, @RequestParam String prefix) {
        if (id == null || id.trim().isEmpty()) {
            System.out.println("Missing guild id");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing guild id");
        }
        if (prefix == null || prefix.isEmpty()) {
            System.out.println("Missing prefix");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing prefix");
        }

        prefix = prefix.replace("\"", "");

        GuildData guild = GuildCache.getGuild(id);

        if(guild == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guild not found");
        }

        if (guild.setPrefix(prefix)) {
            return "Prefix updated to " + prefix;
        } else {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update prefix");
        }
    }

    @GetMapping("{guildId}/users")
    public ResponseEntity<List<Map<String, String>>> getUsers(@PathVariable String id) {
        JDA jda = Bot.getJDA();
        Guild g = jda.getGuildById(id);

        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unvalid guild id. Try with another id.");
        }

        List<Map<String, String>> users = new ArrayList<>();
        for (net.dv8tion.jda.api.entities.Member m : g.getMembers()) {
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("id", m.getId());
            userInfo.put("nickname", m.getNickname());
            userInfo.put("name", m.getUser().getName());
            userInfo.put("icon", m.getUser().getAvatarUrl());
            users.add(userInfo);
        }

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<Map<String, String>>> getLeaderboard(@PathVariable String id) {
        QueryCollection leaderboard = DatabaseHandler.getUsersByExp(id, 0);
        if(leaderboard.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No results");
        }
        return ResponseEntity.ok(leaderboard.toList());
    }
    
    // /api/users/@me/guilds
    @PostMapping("/guilds")
    public ResponseEntity<List<Map<String, String>>> getGuilds(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing guild ids");
        }

        JDA jda = Bot.getJDA();

        List<Map<String, String>> guilds = new ArrayList<>();
        for(String guildId : ids){
            Guild g = jda.getGuildById(guildId);
            if(g == null) {
                continue;
            }

            Map<String, String> guildInfo = new HashMap<>();
            guildInfo.put("id", g.getId());
            guildInfo.put("name", g.getName());
            guildInfo.put("icon", g.getIconId() != null ? g.getIconId() : "");

            guilds.add(guildInfo);
        }

        return ResponseEntity.ok(guilds);
    }

    //api/sound/cose dei suoni
    //api/guild/cose di una guild
        //api/guild/{id}/settings 
    //api/user/cose di un utente
        //api/user/@me/guilds per prendere le guilds di un utente
        //api/user/@me/sounds per prendere i suoni di un utente
    
}
