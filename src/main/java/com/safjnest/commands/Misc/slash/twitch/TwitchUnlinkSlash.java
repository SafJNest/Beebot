package com.safjnest.commands.Misc.slash.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.util.Twitch.TwitchClient;

import com.safjnest.sql.DatabaseHandler;

public class TwitchUnlinkSlash extends SlashCommand{

    public TwitchUnlinkSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true).setAutoComplete(true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        
        String streamerId = TwitchClient.getStreamerId(streamerUsername);

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
