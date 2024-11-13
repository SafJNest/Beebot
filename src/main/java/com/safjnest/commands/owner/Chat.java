package com.safjnest.commands.owner;

import java.util.*;

import com.safjnest.core.chat.ChatHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;


public class Chat extends Command {

    public Chat(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.ownerCommand = true;
        this.hidden = true;

        commandData.setThings(this);
    }

    private List<String> getValidChannelIDs(CommandEvent event, String[] args) {
        String[] argsPlus1 = List.of(args).subList(0, args.length).toArray(new String[0]);

        List<GuildChannel> channels = new ArrayList<GuildChannel>();
        for(String c : argsPlus1) {
            channels.add(PermissionHandler.getMentionedChannelGlobal(event, c));
        }
        channels.removeIf(Objects::isNull);

        List<String> channelIDs = new ArrayList<String>();
        for(GuildChannel c : channels) {
            channelIDs.add(c.getId());
        }

        System.out.println(channelIDs);

        return channelIDs;
    }

    @Override
	protected void execute(CommandEvent event) {
        String[] args = event.getArgs().split(" ");

        List<String> channelIDs;

        switch (args[0].toLowerCase()) {
            case "show":
                event.reply(ChatHandler.showConnections());
                break;
            case "add":
                channelIDs = getValidChannelIDs(event, args);

                ChatHandler.addConnection(channelIDs.toArray(new String[0]));

                event.reply("added " + channelIDs.toString());
                break;
            case "remove":
                ChatHandler.removeAllConnections(args[1]);
                event.reply("removed");
                break;

            case "request":
                channelIDs = getValidChannelIDs(event, args);
                ChatHandler.sendRequest(event.getGuildChannel(), channelIDs);
                break;

            case "omegle":
                ChatHandler.omegle(event.getTextChannel(), false, false, null, null);
                break;

            case "disconnect":
                channelIDs = getValidChannelIDs(event, args);
                channelIDs.add(event.getGuildChannel().getId());
                ChatHandler.removeConnection(channelIDs.toArray(new String[0]));
                break;

            case "omegledisconnect":
                ChatHandler.omegleDisconnect(event.getTextChannel().getId(), null);
                break;
                        
            default:
                event.reply("Unknown command");
                break;
        }
    }

}