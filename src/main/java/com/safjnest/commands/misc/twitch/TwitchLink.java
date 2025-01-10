package com.safjnest.commands.misc.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.TwitchData;

public class TwitchLink extends SlashCommand{

    public TwitchLink(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true),
            new OptionData(OptionType.STRING, "message", "The message that would be sent when the live goes on", true),
            new OptionData(OptionType.CHANNEL, "channel", "Channel where the message would be sent (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.STRING, "sendtype", "How the message would be sent", false)
                .addChoice("Channel", String.valueOf(AlertSendType.CHANNEL.ordinal()))
                .addChoice("Private", String.valueOf(AlertSendType.PRIVATE.ordinal()))
                .addChoice("Both", String.valueOf(AlertSendType.BOTH.ordinal())),
            new OptionData(OptionType.STRING, "private_message", "If empty would be use the same message (Must enable the private option (private or both)", false),
            new OptionData(OptionType.ROLE, "role", "Role that will be pinged when the live goes on (also for send the private message)", false)
            
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        String streamerId = TwitchClient.getStreamerByName(streamerUsername).getId();

        if(streamerId == null){
            event.reply("Streamer not found").queue();
            return;
        }

        String message = event.getOption("message").getAsString();
        String privateMessage = event.getOption("private_message") != null ? event.getOption("private_message").getAsString() : null;

        AlertSendType sendType = event.getOption("sendtype") != null ? AlertSendType.values()[event.getOption("sendtype").getAsInt()] : AlertSendType.CHANNEL;

        String channelID;
        if(event.getOption("channel") != null)
            channelID = event.getOption("channel").getAsString();
        else if(event.getGuild().getSystemChannel() != null)
            channelID = event.getGuild().getSystemChannel().getId();
        else{
            event.deferReply(true).addContent("No channel specified and no system channel found.").queue();
            return;
        }

        String roleID = event.getOption("role") != null ? event.getOption("role").getAsString() : null;

        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuild(guildId);

        TwitchData twitchData = gs.getTwitchdata(streamerId);

        if(twitchData != null) {
            event.deferReply(true).addContent("This streamer has already being linked.").queue();
            return;
        }

        TwitchData newTwitchData = TwitchData.createTwitchData(guildId, streamerId, message, privateMessage, channelID, sendType, roleID);

        if(newTwitchData.getID() == 0) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        gs.getAlerts().put(newTwitchData.getKey(), newTwitchData);
        TwitchClient.registerSubEvent(streamerId);
        event.deferReply(false).addContent("Streamer linked correctly").queue();
    }
}
