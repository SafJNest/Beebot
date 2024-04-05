package com.safjnest.SlashCommands.Settings.Boost;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Guild.GuildData;
import com.safjnest.Utilities.Guild.Alert.AlertData;
import com.safjnest.Utilities.Guild.Alert.AlertType;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BoostCreateSlash extends SlashCommand {

    public BoostCreateSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Boost message", true),
            new OptionData(OptionType.CHANNEL, "channel", "Boost channel (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.BOOLEAN, "private", "If true the bot will send a private message to the user", false)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String boostText = event.getOption("message").getAsString();
        boolean isPrivate = event.getOption("private") != null ? event.getOption("private").getAsBoolean() : false;
        
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

        AlertData newBoost = new AlertData(guildId, boostText, channelID, isPrivate, AlertType.BOOST);
        
        if(newBoost.getID() == 0) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        gs.getAlerts().put(newBoost.getKey(), newBoost);


        event.deferReply(false).addContent("Boost message created.").queue();
    }
}