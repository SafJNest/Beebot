package com.safjnest.commands.Misc.slash.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.util.Twitch.TwitchClient;

import com.safjnest.sql.DatabaseHandler;

public class TwitchUnlinkSlash extends SlashCommand{

    public TwitchUnlinkSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        
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

        DatabaseHandler.deleteTwitchSubscription(streamerId, event.getGuild().getId());

        if (DatabaseHandler.getTwitchSubscriptions(streamerId).getAffectedRows() == 0)
            TwitchClient.unregisterSubEvent(streamerId);

        event.reply("Twitch subscription registered").queue();
    }
}
