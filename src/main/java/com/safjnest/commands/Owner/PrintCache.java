package com.safjnest.commands.Owner;



import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * 
 * @since 1.0
 */
public class PrintCache extends Command {
    
    private GuildDataHandler gs;

    public PrintCache(GuildDataHandler gs) {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.ownerCommand = true;
        this.hidden = true;

        this.gs = gs;
    }

    @Override
    protected void execute(CommandEvent event) {
        ArrayList<String> cache = new ArrayList<>();
        
        int totalUsers = 0;
        int totalChannels = 0;
        int totalAlerts = 0;
        int totalBlackList = 0;
        
        
        
        
        
        String msg = "";
        List<String> forbidden = List.of(CustomEmojiHandler.getForbiddenServers());
        for(GuildData gd : gs.getGuilds().values(false)){
            try {
                if(!forbidden.contains(String.valueOf(gd.getId()))) {
                    long time = gs.getGuilds().getExpirationTime(gd.getID());
                    totalUsers += gd.getUsers().size();
                    totalChannels += gd.getChannels().size();
                    totalAlerts += gd.isAlertsCached() ? 1 : 0;
                    totalBlackList += gd.isBlackListCached() ? 1 : 0;
                    msg += "**" + event.getJDA().getGuildById(gd.getId()).getName() + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                        + "Prefix: " + gd.getPrefix() + "\n"
                        + "ExpSystem: " + (gd.isExpSystemEnabled() ? "enabled" : "disabled") + "\n"
                        + "Members: " + gd.getUsers().size() + "\n"
                        + "Channels: " + gd.getChannels().size() + "\n"
                        + "Alerts: " + (gd.isAlertsCached() ? "cached" : "not cached") + "\n"
                        + "BlackList: " + (gd.isBlackListCached() ? "cached" : "not cached") + "```";
                    cache.add(msg);
                    msg = "";
                }

            } catch (Exception e) {
               continue;
            }
        }

        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getGuilds().size() + " / " + gs.getGuilds().getMaxSize() + "\n"
            + "Total Users: " + Bot.getUsers().size() + " / " + Bot.getUsers().getMaxSize() + "\n"
            + "Total Members: " + totalUsers + "\n"
            + "Total Channels: " + totalChannels + "\n"
            + "Total Alerts: " + totalAlerts + "\n"
            + "Total BlackList: " + totalBlackList + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }

        
    }
}
