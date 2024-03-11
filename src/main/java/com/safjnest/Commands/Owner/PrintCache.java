package com.safjnest.Commands.Owner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Bot.Guild.GuildData;
import com.safjnest.Utilities.Bot.Guild.GuildSettings;
import com.safjnest.Utilities.EXPSystem.ExpSystem;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * 
 * @since 1.0
 */
public class PrintCache extends Command {
    
    private GuildSettings gs;
    private ExpSystem es;

    public PrintCache(GuildSettings gs, ExpSystem es) {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.ownerCommand = true;
        this.hidden = true;

        this.gs = gs;
        this.es = es;
    }

    @Override
    protected void execute(CommandEvent event) {
        String msg = "";
        msg += "Guilds cached: " + gs.cache.size() + "\n";

        HashMap<String, ArrayList<User>> users = new HashMap<>();
        for(String s : es.getUsers().keySet()){
            if(!users.containsKey(s.split("-", 2)[1]))
                users.put(s.split("-", 2)[1], new ArrayList<>());
            users.get(s.split("-", 2)[1]).add(event.getJDA().getUserById(s.split("-", 2)[0]));
        }

        for(GuildData gd : gs.cache.values()){
            try {
                if(!event.getJDA().getGuildById(gd.getId()).getName().startsWith("Beebot")) {
                    msg += "**" + event.getJDA().getGuildById(gd.getId()).getName() + "**```"
                        + "Prefix: " + gd.getPrefix() + "\n"
                        + "ExpSystem: " + (gd.isExpSystemEnabled() ? "enabled" : "disabled") + "\n"
                        + "Users: " + es.getUsers().keySet().stream().filter(s -> s.split("-", 2)[1].equals(String.valueOf(gd.getId()))).count() + "\n"
                        + "Channels: " + gd.getChannels().size() + "\n"
                        + "Alerts: " + (gd.isAlertsCached() ? "cached" : "not cached") + "\n"
                        + "BlackList: " + (gd.isBlackListCached() ? "cached" : "not cached") + "```";

                }

            } catch (Exception e) {
               continue;
            }
            
        }
        
        MessageChannel channel = event.getChannel();
        try {
            if (msg.length() > 2000) {
                File supp = new File("prefix.txt");
                FileWriter app;
                try {
                    app = new FileWriter(supp);
                    app.write(msg);
                    app.flush();
                    app.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                channel.sendMessage("Too many prefixes to send.").queue();
                channel.sendFiles(FileUpload.fromData(supp)).queue();
            } else {
                channel.sendMessage(msg).queue();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            channel.sendMessage(e.getMessage()).queue();
        }
    }
}
