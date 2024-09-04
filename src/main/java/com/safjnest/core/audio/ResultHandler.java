package com.safjnest.core.audio;

import java.util.Collections;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.types.*;
import com.safjnest.util.SafJNest;
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

    private final ReplyType replyType;
    private final PlayTiming playbackTiming;
    

    public ResultHandler(CommandEvent commandEvent, boolean isSearch, PlayTiming playbackTiming) {
        this.commandEvent = commandEvent;
        this.slashCommandEvent = null;
        this.guild = commandEvent.getGuild();
        this.author = commandEvent.getMember();
        this.args = commandEvent.getArgs();
        this.isSearch = isSearch;
        this.pm = PlayerManager.get();
        this.ts = pm.getGuildMusicManager(guild).getTrackScheduler();
        this.playbackTiming = playbackTiming;
        this.replyType = ReplyType.SEPARATED;
    }

    public ResultHandler(SlashCommandEvent slashCommandEvent, boolean isSearch, String args, PlayTiming playbackTiming, ReplyType replyType) {
        this.commandEvent = null;
        this.slashCommandEvent = slashCommandEvent;
        this.guild = slashCommandEvent.getGuild();
        this.author = slashCommandEvent.getMember();
        this.playbackTiming = playbackTiming;
        this.args = args;
        this.isSearch = isSearch;
        this.pm = PlayerManager.get();
        this.ts = pm.getGuildMusicManager(guild).getTrackScheduler();
        this.replyType = replyType;
    }


    private void sendEmbed() {
        if(commandEvent != null) QueueHandler.sendEmbed(commandEvent, EmbedType.PLAYER);
        else if(slashCommandEvent != null) QueueHandler.sendEmbed(slashCommandEvent, EmbedType.PLAYER, ReplyType.SEPARATED);
    }

    private void replySlash(String message) {
        switch (replyType) {
            case REPLY:
                slashCommandEvent.reply(message).queue();
                break;
            case MODIFY:
                slashCommandEvent.getHook().editOriginal(message).queue();
                break;
            case SEPARATED:
                slashCommandEvent.getHook().sendMessage(message).queue();
                break;
            default:
                break;
        }
    }

    private void replyEmbedsSlash(MessageEmbed messageEmbed) {
        switch (replyType) {
            case REPLY:
                slashCommandEvent.replyEmbeds(messageEmbed).queue();
                break;
            case MODIFY:
                slashCommandEvent.getHook().editOriginalEmbeds(messageEmbed).queue();
                break;
            case SEPARATED:
                slashCommandEvent.getHook().sendMessageEmbeds(messageEmbed).queue();
                break;
            default:
                break;
        }
    }

    private void reply(String message) {
        if(commandEvent != null) commandEvent.reply(message);
        else if(slashCommandEvent != null) replySlash(message);
    }

    private void reply(MessageEmbed messageEmbed) {
        if(commandEvent != null) commandEvent.reply(messageEmbed);
        else if(slashCommandEvent != null) replyEmbedsSlash(messageEmbed);
    }

    private void search() {
        if(commandEvent != null) 
            pm.loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(commandEvent, true, playbackTiming));
        else if(slashCommandEvent != null) 
            pm.loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(slashCommandEvent, true, args, playbackTiming, replyType));
    }


    private void queue(AudioTrack track, PlayTiming playbackTiming, int seconds) {
        if(seconds != -1)
            track.setPosition(seconds * 1000);

        switch (playbackTiming) {
        case NOW:
            ts.addTrackToFront(track);

            ts.moveCursor(1);
            ts.play(track, true);
        break;
        case NEXT:
            ts.addTrackToFront(track);

            if (ts.canPlay()) {
                ts.play();
            }
        break;
        case LAST:
            ts.queue(track);
            
            if (ts.canPlay()) {
                ts.moveCursor(ts.getQueue().size(), true);
                ts.play();
            }
        break;
        default:
            ts.queue(track);
                
            if (ts.canPlay()) {
                ts.moveCursor(ts.getQueue().size(), true);
                ts.play();
            }
        break;
        }
    }

    private void queue(AudioTrack track, PlayTiming playbackTiming) {
        queue(track, playbackTiming, -1);
    }

    private void queue(AudioPlaylist playlist, PlayTiming playbackTiming) {

        if(playlist.getTracks().size() == 0) { 
            reply("Playlist is empty");
            return;
        }

        switch (playbackTiming) {
            case NOW:
                Collections.reverse(playlist.getTracks());
                for(AudioTrack track : playlist.getTracks()) {
                    track.setUserData(new TrackData(AudioType.AUDIO));
                    ts.addTrackToFront(track);
                }
                ts.play(ts.moveCursor(1), true);
                break;
            case NEXT:
                Collections.reverse(playlist.getTracks());
                for(AudioTrack track : playlist.getTracks()) {
                    track.setUserData(new TrackData(AudioType.AUDIO));
                    ts.addTrackToFront(track);
                }
                if (ts.canPlay()) {
                    ts.play();
                }
                break;  
            case LAST:
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
                break;
        
            default:
                break;
        }


    }

    
    @Override
    public void trackLoaded(AudioTrack track) {
        track.setUserData(new TrackData(AudioType.AUDIO));

        int seconds = SafJNest.extractSeconds(args);
        queue(track, playbackTiming, seconds);

        reply(QueueHandler.getTrackEmbed(author, track));

        guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

        sendEmbed();
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        if(isSearch) {
            AudioTrack track = playlist.getTracks().get(0);
            track.setUserData(new TrackData(AudioType.AUDIO));

            queue(track, playbackTiming);

            reply(QueueHandler.getTrackEmbed(author, track));
        }
        else {
            queue(playlist, playbackTiming);

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