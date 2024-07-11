package com.safjnest.commands.Misc.slash.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.util.Twitch.TwitchClient;

import com.safjnest.sql.DatabaseHandler;

public class TwitchLinkSlash extends SlashCommand{

    public TwitchLinkSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true),
            new OptionData(OptionType.CHANNEL, "channel", "Channel", false)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.STRING, "message", "The message that would be sent when the live goes on", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        String channel = event.getOption("channel") == null ? event.getTextChannel().getId() : event.getOption("channel").getAsString();
        String message = event.getOption("message") == null ? "" : event.getOption("message").getAsString();

        String streamerId = TwitchClient.getStreamerByName(streamerUsername).getId();

        if(streamerId == null){
            event.reply("Streamer not found").queue();
            return;
        }

        if (DatabaseHandler.updateTwitchSubscription(streamerId, event.getGuild().getId(), channel, message)) {
            event.reply("Twitch subscription already exists for this streamer so it got updated").queue();
            return;
        }

        if (channel == null) {
            event.reply("For linking new subscriptions you must specify the channel").queue();
            return;
        }

        TwitchClient.registerSubEvent(streamerId);
        DatabaseHandler.setTwitchSubscriptions(streamerId, event.getGuild().getId(), channel, message);

        event.reply("Twitch subscription registered").queue();
    }
}
