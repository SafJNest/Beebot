package com.safjnest.commands.settings.slash.levelup;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LevelUpChannelToggleSlash extends SlashCommand{
    private GuildDataHandler gs;

    public LevelUpChannelToggleSlash(String father, GuildDataHandler gs){
        this.gs = gs;
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Channel to enable/disable exp gain", true)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.STRING, "toggle", "on or off", true)
                .addChoice("on", "on")
                .addChoice("off", "off")
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String channelId = event.getOption("channel").getAsChannel().getId();
        boolean toggle = event.getOption("toggle").getAsString().equalsIgnoreCase("on") ? true : false;

        String guildId = event.getGuild().getId();

        if(!gs.getGuild(guildId).getChannelData(channelId).setExpEnabled(toggle)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        if(!toggle){
            event.deferReply(false).addContent("This channel no longer gives exp.").queue();
            return;
        }
        event.deferReply(false).addContent("This channel gives exp now.").queue();
    }
}