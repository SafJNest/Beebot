package com.safjnest.commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.CommandsLoader;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.Twitch.TwitchClient;
import java.util.List;

/**
 * Create twitch subscription
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since idk
 */

public class Twitch extends Command {

    public Twitch(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.hidden = true;
    }

    @Override
    protected void execute(CommandEvent e) {
        //TwitchClient.registerSubEvent("126371014"); //Sunny314_
        //TwitchClient.registerSubEvent("164078841"); //leon4117
        String streamerUsername = "leon4117";
        String guildId = "474935164451946506";
        String channelId = "938513359626715176";
        
        String streamerId = TwitchClient.getClient().getHelix().getUsers(null, null, List.of(streamerUsername)).execute().getUsers().get(0).getId();
        TwitchClient.registerSubEvent(streamerId);
        DatabaseHandler.setTwitchSubscriptions(streamerId, guildId, channelId, null);
    }
   
}