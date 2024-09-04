package com.safjnest.commands.audio.slash.playlist;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import com.safjnest.sql.*;
import com.safjnest.core.audio.*;
import com.safjnest.core.audio.types.*;


import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistPlaySlash extends SlashCommand {

    public PlaylistPlaySlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of the custom playlist", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "timing", "When to play the track", false)
                .addChoice(PlayTiming.NOW.getName(), String.valueOf(PlayTiming.NOW.ordinal()))
                .addChoice(PlayTiming.NEXT.getName(), String.valueOf(PlayTiming.NEXT.ordinal()))
                .addChoice(PlayTiming.LAST.getName(), String.valueOf(PlayTiming.LAST.ordinal()))
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(false).queue();

        int playlistId;
        try {
            playlistId = event.getOption("playlist-name").getAsInt();
        } catch (Exception e) {
            event.getHook().editOriginal("What the fock are yu doing.").queue();
            return;
        }

        PlayTiming timing = event.getOption("timing") == null ? PlayTiming.LAST : PlayTiming.values()[event.getOption("timing").getAsInt()];
 
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();

        if(myChannel == null){
            event.getHook().editOriginal("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.getHook().editOriginal("The bot is already being used in another voice channel.").queue();
            return;
        }

        
        ResultRow playlist = DatabaseHandler.getPlaylist(event.getUser().getId(), playlistId);
        QueryResult playlistTracks = DatabaseHandler.getPlaylistTracks(playlistId, null, null);

        if (playlistTracks.isEmpty()) {
            event.getHook().editOriginal("Playlist is empty.").queue();
            return;
        }

        List<AudioTrack> tracksFinal = new ArrayList<>();
        for(ResultRow trackToLoad : playlistTracks) 
            tracksFinal.add(PlayerManager.get().decodeTrack(trackToLoad.get("encoded_track")));
        

        SafjAudioPlaylist audioPlaylist = new SafjAudioPlaylist(playlist.get("name"), tracksFinal, null);
        (new ResultHandler(event, false, "", timing, ReplyType.MODIFY)).playlistLoaded(audioPlaylist);
    }
}
