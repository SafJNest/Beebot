package com.safjnest.commands.audio.playlist;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistCreate extends SlashCommand{

    public PlaylistCreate(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Name of the playlist to create", true),
            new OptionData(OptionType.BOOLEAN, "load", "Load the current queue in the newly created playlist", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();

        String playlistName = event.getOption("name").getAsString();
        boolean loadQueue = event.getOption("load") != null ? event.getOption("load").getAsBoolean() : false;
        String userId = event.getUser().getId();

        Integer maxPlaylists = !PermissionHandler.isUntouchable(userId)
                ? (!PermissionHandler.isPremium(userId) ? Bot.getSettings().maxFreePlaylists :  Bot.getSettings().maxPremiumPlaylists)
                : Integer.valueOf(Integer.MAX_VALUE);

        Integer maxPlaylistSize = !PermissionHandler.isUntouchable(userId)
                ? (!PermissionHandler.isPremium(userId) ? Bot.getSettings().maxFreePlaylistSize : Bot.getSettings().maxPremiumPlaylistSize)
                : Integer.valueOf(Integer.MAX_VALUE);

        QueryCollection userPlaylists = DatabaseHandler.getPlaylists(userId);

        if(userPlaylists.size() >= maxPlaylists && !PermissionHandler.isPremium(userId)) {
            event.getHook().editOriginal("You have already created the maximum amount of free playlists (for more playlists wait for future paid tiers [pagaaaah, sgancia, spilla, sborsa proprio maonna ragazih]).").queue();
            return;
        }

        if(userPlaylists.size() >= maxPlaylists && PermissionHandler.isPremium(userId)) {
            event.getHook().editOriginal("You have already created the maximum amount of playlists. If you legitimately need more DM ono f the devs.").queue();
            return;
        }

        if(userPlaylists.arrayColumn("name").contains(playlistName)) {
            event.getHook().editOriginal("You have already created a playlist with that name.").queue();
            return;
        }

        int playlistId = DatabaseHandler.createPlaylist(playlistName, userId);

        if (!loadQueue) {
            event.getHook().editOriginal("Playlist created successfully.").queue();
            return;
        }

        List<AudioTrack> queue = (List<AudioTrack>) PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler().getQueue();
        if(queue.isEmpty()) {
            event.getHook().editOriginal("The current queue is empty so the created playlist will be empty.").queue();
            return;
        }

        if(queue.size() > maxPlaylistSize) {
            DatabaseHandler.addTrackToPlaylist(playlistId, queue.subList(0, maxPlaylistSize - 1), null);
            event.getHook().editOriginal("Playlist created successfully, the queue was too big to fit in the playlist (max " + maxPlaylistSize + ") so only the first " + maxPlaylistSize + "tracks were put in.").queue();
            return;
        }

        DatabaseHandler.addTrackToPlaylist(playlistId, queue, null);

        event.getHook().editOriginal("Playlist created successfully.").queue();
    }
}
