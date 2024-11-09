package com.safjnest.commands.misc.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class TwitchMessage extends SlashCommand {

    public TwitchMessage(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username to change alert message", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "message", "Message that would be sent when a streamer is live", true),
            new OptionData(OptionType.STRING, "sendtype", "How the message would be sent", false)
                .addChoice("Channel", String.valueOf(AlertSendType.CHANNEL.ordinal()))
                .addChoice("Private", String.valueOf(AlertSendType.PRIVATE.ordinal()))
                .addChoice("Both", String.valueOf(AlertSendType.BOTH.ordinal())),
            new OptionData(OptionType.STRING, "private_message", "If empty would be use the same message (Must enable the private option (private or both)", false)
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

        String message = event.getOption("message") != null ? event.getOption("message").getAsString().replace("'", "''") : null;
        String privateText = event.getOption("private_message") != null ? event.getOption("private_message").getAsString() : null;
        
        AlertSendType sendType = event.getOption("sendtype") != null ? AlertSendType.values()[event.getOption("sendtype").getAsInt()] : AlertSendType.CHANNEL;
        
        String guildId = event.getGuild().getId();
        GuildData gs = Bot.getGuildData(guildId);
        
        TwitchData twitch = gs.getTwitchdata(streamerId);




        if(twitch == null) {
            event.deferReply(true).addContent("This guild doesn't have a twitch subscription for this streamer.").queue();
            return;
        }

        boolean result = false;
        switch (sendType) {
            case CHANNEL:
                result = twitch.setMessage(message);
                break;
            case PRIVATE:
                if (twitch.getStreamerRole() == null) {
                    event.deferReply(true).addContent("You need to set a role to send private messages").queue();
                    return;
                }
                result = twitch.setPrivateMessage(privateText) && twitch.setSendType(sendType);
                break;
            case BOTH:
                if (twitch.getStreamerRole() == null) {
                    event.deferReply(true).addContent("You need to set a role to send private messages").queue();
                    return;
                }
                result = twitch.setMessage(message) && twitch.setPrivateMessage(privateText) && twitch.setSendType(sendType);
                break;
        
            default:
                break;
        }
        
        if (!result) {
            event.deferReply(true).addContent("Something went wrong. Use `/help twitch message` for more information").queue();
            return;
        }
        
        event.deferReply(false).addContent("Twitch alert changed correctly.").queue();
    }
}
