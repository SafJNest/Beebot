package com.safjnest.Utilities.Controller.Interface;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safjnest.Utilities.Bot.BotSettingsHandler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;



@RestController
@RequestMapping("/api")
public class ApiController {
    
    private BotSettingsHandler bs;

    @Autowired
    public ApiController(BotSettingsHandler bs) {
        this.bs = bs;
    }

    @GetMapping("/{id}")
    public String getEmployeeById(@PathVariable String id) {
        return bs.getSettings(id).prefix;
    }

    @GetMapping("/{id}/guilds")
    public String getEmployeeByIdAndGuilds(@PathVariable String id, @RequestBody List<String> ids) {
        JDA jda = bs.getSettings(id).getJda();
        String list = "[";
        for(String guildId : ids){
            try {
                Guild g = jda.getGuildById(guildId);                        
                list += "{\"id\":\"" + g.getId() + "\",\"name\":\"" + g.getName() + "\" ,\"icon\":\"" + g.getIconUrl() + "\"},";
            } catch (Exception e) {
               
            } 
        }
        list = list.substring(0, list.length()-1);
        return list + "]";
    }

    @PostMapping("/{id}/{guildId}/prefix")
    public String setPrefix(@PathVariable String id, @PathVariable String guildId, @RequestBody String prefix) {
        prefix = prefix.replace("\"", "");
        bs.getSettings(id).getGuildSettings().getServer(guildId).setPrefix(prefix);
        System.out.println(bs.getSettings(id).getGuildSettings().getServer(guildId).getPrefix());   
        return bs.getSettings(id).getGuildSettings().getServer(guildId).getPrefix();
    }


}
