package com.safjnest.commands.audio.playlist;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistAddQueue extends SlashCommand {

    public PlaylistAddQueue(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of the custom playlist", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();

        int playlistId = event.getOption("playlist-name").getAsInt();//ti da direttamente l'id della playlist uwu gna gna
        String userId = event.getUser().getId();
        Integer maxPlaylistSize = !PermissionHandler.isUntouchable(userId)
                ? (!PermissionHandler.isPremium(userId) ? SettingsLoader.getSettings().getBotSettings().getMaxFreePlaylistSize() : SettingsLoader.getSettings().getBotSettings().getMaxPremiumPlaylistSize())
                : Integer.valueOf(Integer.MAX_VALUE);

        List<AudioTrack> queue = (List<AudioTrack>) PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler().getQueue();
        if(queue.isEmpty()) {
            event.getHook().editOriginal("The current queue is empty.").queue();
            return;
        }

        int playlistSize = DatabaseHandler.getPlaylistTracks(playlistId, null, null).size();

        if(queue.size() + playlistSize >= maxPlaylistSize) {
            DatabaseHandler.addTrackToPlaylist(playlistId, queue.subList(0, maxPlaylistSize - playlistSize - 1), null);
            event.getHook().editOriginal("The queue was too big to fit in the playlist (max " + maxPlaylistSize + ") so only the first " + (maxPlaylistSize - playlistSize - 1) + "tracks were put in.").queue();
            return;
        }

        DatabaseHandler.addTrackToPlaylist(playlistId, queue, null);

        event.getHook().editOriginal("Queue added to the playlist successfully.").queue();
    }
}
