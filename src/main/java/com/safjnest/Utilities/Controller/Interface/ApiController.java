package com.safjnest.Utilities.Controller.Interface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.Utilities.Bot.BotData;
import com.safjnest.Utilities.Bot.BotDataHandler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;



@RestController
@RequestMapping("/api")
public class ApiController {
    
    private BotDataHandler bs;

    @Autowired
    public ApiController(BotDataHandler bs) {
        this.bs = bs;
    }

    @GetMapping("/{id}")
    public String getEmployeeById(@PathVariable String id) {
        BotData settings = bs.getSettings(id);
        if (settings == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unvalid endpoint. Try with another id.");
        }
        return settings.prefix;
    }

    @GetMapping("/{id}/guilds")
    public ResponseEntity<List<Map<String, String>>> getEmployeeByIdAndGuilds(@PathVariable String id, @RequestBody List<String> ids) {
        JDA jda = bs.getSettings(id).getJda();
        List<Map<String, String>> guilds = new ArrayList<>();
        for(String guildId : ids){
            try {
                Guild g = jda.getGuildById(guildId);
                Map<String, String> guildInfo = new HashMap<>();
                guildInfo.put("id", g.getId());
                guildInfo.put("name", g.getName());
                guildInfo.put("icon", g.getIconUrl());
                guilds.add(guildInfo);
            } catch (Exception ignored) { }
        }
        return ResponseEntity.ok(guilds);
    }

    @PostMapping("/{id}/{guildId}/prefix")
    public String setPrefix(@PathVariable String id, @PathVariable String guildId, @RequestBody(required = false) String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prefix is required");
        }
        prefix = prefix.replace("\"", "");
        bs.getSettings(id).getGuildSettings().getServer(guildId).setPrefix(prefix);
        DatabaseHandler.setPrefix(guildId, id, prefix);
        return bs.getSettings(id).getGuildSettings().getServer(guildId).getPrefix();
    }

    @GetMapping("/{id}/{guildId}")
    public ResponseEntity<Map<String, String>> getGuild(@PathVariable String id, @PathVariable String guildId) {
        BotData settings = bs.getSettings(id);
        if (settings == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unvalid endpoint. Try with another id.");
        }
        JDA jda = settings.getJda();
        Guild g = jda.getGuildById(guildId);
        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unvalid guild id. Try with another id.");
        }
        Map<String, String> guildInfo = new HashMap<>();
        guildInfo.put("id", g.getId());
        guildInfo.put("name", g.getName());
        guildInfo.put("icon", g.getIconUrl());
        return ResponseEntity.ok(guildInfo);
    }

    @GetMapping("/{id}/{guildId}/users")
    public ResponseEntity<List<Map<String, String>>> getUsers(@PathVariable String id, @PathVariable String guildId) {
        BotData settings = bs.getSettings(id);
        if (settings == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unvalid endpoint. Try with another id.");
        }
        JDA jda = settings.getJda();
        Guild g = jda.getGuildById(guildId);
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

    @GetMapping("/{guildId}/leaderboard")
    public ResponseEntity<List<Map<String, String>>> getLeaderboard(@PathVariable String guildId) {
        QueryResult leaderboard = DatabaseHandler.getUsersByExp(guildId, 0);
        if(leaderboard.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No results");
        }
        return ResponseEntity.ok(leaderboard.toList());


    }


}
