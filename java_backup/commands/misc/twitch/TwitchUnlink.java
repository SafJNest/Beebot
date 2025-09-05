package com.safjnest.commands.misc.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.database.BotDB;

public class TwitchUnlink extends SlashCommand{

    public TwitchUnlink(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true).setAutoComplete(true)
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

        GuildCache.getGuildOrPut(event.getGuild().getId()).deleteAlert(AlertType.TWITCH, streamerId);

        if (BotDB.getTwitchSubscriptions(streamerId).size() == 0)
            TwitchClient.unregisterSubEvent(streamerId);

        event.reply("Twitch subscription registered").queue();
    }
}
