package com.safjnest.commands.audio.playlist;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SettingsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class PlaylistAddSong extends SlashCommand{

    public PlaylistAddSong(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of your playlist to add a track to", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "url", "URL of the track to add to the playlist", true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();
        
        int playlistId = event.getOption("playlist-name").getAsInt();
        String url = event.getOption("url").getAsString();

        String userId = event.getUser().getId();
        Integer maxPlaylistSize = !PermissionHandler.isUntouchable(userId)
                ? (!PermissionHandler.isPremium(userId) ? SettingsLoader.getSettings().getBotSettings().getMaxFreePlaylistSize() : SettingsLoader.getSettings().getBotSettings().getMaxPremiumPlaylistSize())
                : Integer.valueOf(Integer.MAX_VALUE);

        AudioTrack track = PlayerManager.get().createTrack(event.getGuild(), url);

        if(track == null) {
            event.getHook().editOriginal("No track found.").queue();
            return;
        }
        
        int playlistSize = BotDB.getPlaylistTracks(playlistId, null, null).size();
        if(playlistSize >= maxPlaylistSize) {
            event.getHook().editOriginal("You already reached the maximum number f tracks (" + maxPlaylistSize + ") in a playlist.").queue();
            return;
        }

        BotDB.addTrackToPlaylist(playlistId, List.of(track), null);

        event.getHook().editOriginal("Track added to the playlist successfully.").queue();
    }
    
}
