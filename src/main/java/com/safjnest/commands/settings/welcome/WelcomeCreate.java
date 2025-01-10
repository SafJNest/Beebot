package com.safjnest.commands.settings.welcome;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import com.safjnest.core.cache.managers.GuildCache;

public class WelcomeCreate extends SlashCommand{

    public WelcomeCreate(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Welcome message", true),
            new OptionData(OptionType.CHANNEL, "channel", "Welcome channel (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.STRING, "sendtype", "How the message would be sent", false)
                .addChoice("Channel", String.valueOf(AlertSendType.CHANNEL.ordinal()))
                .addChoice("Private", String.valueOf(AlertSendType.PRIVATE.ordinal()))
                .addChoice("Both", String.valueOf(AlertSendType.BOTH.ordinal())),
            new OptionData(OptionType.STRING, "private_message", "If empty would be use the same message (Must enable the private option (private or both)", false),
            new OptionData(OptionType.ROLE, "role", "Role that will be given to the new members.", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String welcomeText = event.getOption("message").getAsString();
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

        String roleID = event.getOption("role") != null ? event.getOption("role").getAsString() : null;

        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuild(guildId);

        AlertData welcome = gs.getAlert(AlertType.WELCOME);

        if(welcome != null) {
            event.deferReply(true).addContent("A welcome message already exists.").queue();
            return;
        }

        String[] roles = new String[]{roleID};

        AlertData newWelcome = new AlertData(guildId, welcomeText, privateText, channelID, sendType, roles);

        if(newWelcome.getID() == 0) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        gs.getAlerts().put(newWelcome.getKey(), newWelcome);
        event.deferReply(false).addContent("Welcome message created.").queue();
    }
}
