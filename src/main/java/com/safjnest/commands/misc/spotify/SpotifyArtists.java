package com.safjnest.commands.misc.spotify;


import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.SpotifyMessageType;
import com.safjnest.util.spotify.SpotifyTimeRange;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SpotifyArtists extends SlashCommand {

    public SpotifyArtists(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        this.options = List.of(
            new OptionData(OptionType.STRING, "time_range", "The time range for the tracks", false)
                .setRequired(false)
                .addChoice("Short Term", "short_term")
                .addChoice("Medium Term", "medium_term")
                .addChoice("Long Term", "long_term")
                .addChoice("Full Term", "full_term")
        );

        commandData.setThings(this);
    }
    
    @Override
    protected void execute(SlashCommandEvent event) {
        String userId = event.getUser().getId();
        SpotifyTimeRange timeRange = event.getOption("time_range") != null 
            ? SpotifyTimeRange.valueOf(event.getOption("time_range").getAsString().toUpperCase()) 
            : SpotifyTimeRange.SHORT_TERM;

        event.deferReply(false).queue();
        SpotifyMessage.send(event.getHook(), userId, SpotifyMessageType.ARTISTS, 0, timeRange);
    }
}
