package com.safjnest.Utilities.Audio;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.SafJNest;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class ResultHandler implements AudioLoadResultHandler {
    private final TrackScheduler ts;
    private final PlayerManager pm;

    private final CommandEvent commandEvent;
    private final SlashCommandEvent slashCommandEvent;

    private final Guild guild;
    private final Member author;
    private final String args;
    private final boolean isSearch;

    private final boolean isForced;
    

    public ResultHandler(CommandEvent commandEvent, PlayerManager pm, boolean isSearch, boolean isForced) {
        this.commandEvent = commandEvent;
        this.slashCommandEvent = null;
        this.guild = commandEvent.getGuild();
        this.author = commandEvent.getMember();
        this.args = commandEvent.getArgs();
        this.isSearch = isSearch;
        this.pm = pm;
        this.ts = pm.getGuildMusicManager(guild).getTrackScheduler();
        this.isForced = isForced;
    }

    public ResultHandler(SlashCommandEvent slashCommandEvent, PlayerManager pm, boolean isSearch, String args, boolean isForced) {
        this.commandEvent = null;
        this.slashCommandEvent = slashCommandEvent;
        this.guild = slashCommandEvent.getGuild();
        this.author = slashCommandEvent.getMember();
        this.args = args;
        this.isSearch = isSearch;
        this.pm = pm;
        this.ts = pm.getGuildMusicManager(guild).getTrackScheduler();
        this.isForced = isForced;
    }


    private void sendEmbed() {
        if(commandEvent != null) QueueHandler.sendEmbed(commandEvent, EmbedType.PLAYER);
        else if(slashCommandEvent != null) QueueHandler.sendEmbed(slashCommandEvent, EmbedType.PLAYER, true);
    }

    private void reply(String message) {
        if(commandEvent != null) commandEvent.reply(message);
        else if(slashCommandEvent != null) slashCommandEvent.reply(message).queue();
    }

    private void reply(MessageEmbed messageEmbed) {
        if(commandEvent != null) commandEvent.reply(messageEmbed);
        else if(slashCommandEvent != null) slashCommandEvent.replyEmbeds(messageEmbed).queue();
    }

    private void search() {
        if(commandEvent != null) 
            pm.loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(commandEvent, pm, true, isForced));
        else if(slashCommandEvent != null) 
            pm.loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(slashCommandEvent, pm, true, args, isForced));
    }


    private void queue(AudioTrack track, boolean isForced, int seconds) {
        if(isForced) {
            if(seconds != -1)
                track.setPosition(seconds * 1000);

            ts.addTrackToFront(track);
            ts.play(ts.moveCursor(1), true);
        }
        else {
            ts.queue(track);
            if (ts.canPlay()) {
                ts.moveCursor(ts.getQueue().size(), true);
                ts.play();
            }
        }
    }

    private void queue(AudioTrack track, boolean isForced) {
        queue(track, isForced, -1);
    }

    private void queue(AudioPlaylist playlist, boolean isForced) {
        if(isForced) {
            //TODO capire se il comportamento deve essere diverso
        }
        
        if(playlist.getTracks().size() == 0) { 
            reply("Playlist is empty");
            return;
        }

        for(AudioTrack track : playlist.getTracks()) {
            track.setUserData(new TrackData(AudioType.AUDIO));
            ts.queue(track);
        }

        if (ts.canPlay()) {
            int index = ts.getQueue().size() - playlist.getTracks().size();
            if (index > 0) index++;
            ts.moveCursor(index, true);
            ts.play();
        }
    }

    
    @Override
    public void trackLoaded(AudioTrack track) {
        track.setUserData(new TrackData(AudioType.AUDIO));

        int seconds = SafJNest.extractSeconds(args);
        queue(track, isForced, seconds);

        reply(QueueHandler.getTrackEmbed(author, track));

        guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

        sendEmbed();
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        if(isSearch) {
            AudioTrack track = playlist.getTracks().get(0);
            track.setUserData(new TrackData(AudioType.AUDIO));

            queue(track, isForced);

            reply(QueueHandler.getTrackEmbed(author, track));
        }
        else {
            queue(playlist, isForced);

            reply(QueueHandler.getPlaylistEmbed(author, playlist, args));
        }
        guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

        sendEmbed();
    }

    @Override
    public void noMatches() {
        if(!isSearch)
            search();
        else
            reply("No matches");
    }

    @Override
    public void loadFailed(FriendlyException throwable) {
        reply(throwable.getMessage());
    }
}