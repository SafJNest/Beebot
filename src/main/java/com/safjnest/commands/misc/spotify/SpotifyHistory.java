package com.safjnest.commands.misc.spotify;

import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.spotify.SpotifyHandler;
import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.type.SpotifyMessageType;
import com.safjnest.util.spotify.type.SpotifyTimeRange;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SpotifyHistory extends SlashCommand{
    public SpotifyHistory(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String userId = event.getUser().getId();
        SpotifyTimeRange timeRange = null;

        event.deferReply(false).queue();
        SpotifyMessage.send(event.getHook(), userId, SpotifyMessageType.HISTORY, 0, timeRange);

        //List<SpotifyTrack> tracks = SpotifyHandler.getHistoryFromSpotifyApi(event.getUser().getId(), 5, 0);
        //event.reply("Your recently played tracks:\n" + tracks.stream().map(t -> t.getName() + " by " + t.getArtist()).reduce((a, b) -> a + "\n" + b).orElse("No tracks found")).queue();
    }
}
