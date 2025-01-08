package com.safjnest.commands.settings.welcome;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import com.safjnest.core.cache.managers.GuilddataCache;

public class WelcomeChannel extends SlashCommand{

    public WelcomeChannel(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "The new welcome channel (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String channelID;
        if(event.getOption("channel") != null)
            channelID = event.getOption("channel").getAsString();
        else if(event.getGuild().getSystemChannel() != null)
            channelID = event.getGuild().getSystemChannel().getId();
        else {
            event.deferReply(true).addContent("No channel specified and no system channel found.").queue();
            return;
        }

        String guildId = event.getGuild().getId();

        GuildData gs = GuilddataCache.getGuild(guildId);

        AlertData welcome = gs.getAlert(AlertType.WELCOME);

        if(welcome == null) {
            event.deferReply(true).addContent("This guild doesn't have a welcome message. Use the create command.").queue();
            return;
        }

        if (!welcome.setAlertChannel(channelID)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        event.deferReply(false).addContent("Changed welcome channel.").queue();
    }
}
