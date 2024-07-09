package com.safjnest.commands.Settings.slash.Boost;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BoostCreateSlash extends SlashCommand {

    public BoostCreateSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Boost message", true),
            new OptionData(OptionType.CHANNEL, "channel", "Boost channel (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT),
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
        String boostText = event.getOption("message").getAsString();
        String privateText = event.getOption("private_message") != null ? event.getOption("private_message").getAsString() : null;
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

        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);

        AlertData boost = gs.getAlert(AlertType.BOOST);

        if(boost != null) {
            event.deferReply(true).addContent("A boost message already exists.").queue();
            return;
        }

        AlertData newBoost = new AlertData(guildId, boostText, privateText, channelID, sendType, AlertType.BOOST);
        
        if(newBoost.getID() == 0) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        gs.getAlerts().put(newBoost.getKey(), newBoost);


        event.deferReply(false).addContent("Boost message created.").queue();
    }
}