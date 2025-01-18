package com.safjnest.controller;


import com.safjnest.core.cache.managers.GuildCache;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

public class Postman {
    
    private JDA jda;

    public Postman(JDA jda, GuildCache gs){

        this.jda = jda;


    }

    public void willBeRemovedSoon(){
          /**
         * Smurfing
         */
    }

    public String getServerList(String userId, String ids){
        String list = "[";
        for(String id : ids.split("/")){
            try {
                Guild g = jda.getGuildById(id);                        
                list += "{\"id\":\"" + g.getId() + "\",\"name\":\"" + g.getName() + "\" ,\"icon\":\"" + g.getIconUrl() + "\"},";
            } catch (Exception e) {
               
            }
        }
        list = list.substring(0, list.length()-1);
        return list + "]";
    }

    public String getHomeStats(){
        int cont_guilds = 0;
        int cont_user = 0;
        for(Guild g : jda.getGuilds()){
            if(g.getName().startsWith("BeebotLOL"))
                continue;
            cont_user += g.getMemberCount();
            cont_guilds++;
        }
        String list = "{\"cont_guilds\":"+  cont_guilds + ", \"cont_user\":"+cont_user+", \"cont_command\":60}";
        return list;
    }

    public String getPrefix(String userId, String guilId){
        
        String list = "{\"prefix\":" + "\"" + GuildCache.getGuild(userId).getPrefix() + "\"" + "}";
        return list;
    }
    

    
}
