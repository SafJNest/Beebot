package com.safjnest.commands.Misc.slash.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.util.Twitch.TwitchClient;

import com.safjnest.sql.DatabaseHandler;

public class TwitchLinkSlash extends SlashCommand{

    public TwitchLinkSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true),
            new OptionData(OptionType.CHANNEL, "channel", "Channel", true)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.STRING, "message", "The message that would be sent when the live goes on", false)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        String channel = event.getOption("channel").getAsChannel().getId();
        String message = event.getOption("message") == null ? "" : event.getOption("message").getAsString();

        String streamerId = TwitchClient.getStreamerByName(streamerUsername).getId();

        if(streamerId == null){
            event.reply("Streamer not found").queue();
            return;
        }

        if (!DatabaseHandler.getTwitchSubscriptionsGuild(streamerId, event.getGuild().getId()).emptyValues()) {
            DatabaseHandler.updateTwitchSubscription(streamerId, event.getGuild().getId(), channel, message);
            event.reply("Twitch subscription already existed for this streamer in this server so it got updated").queue();
            return;
        }

        TwitchClient.registerSubEvent(streamerId);
        DatabaseHandler.setTwitchSubscriptions(streamerId, event.getGuild().getId(), channel, message);

        event.reply("Twitch subscription registered").queue();
    }
}
