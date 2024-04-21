package com.safjnest.SlashCommands.Audio.Play;

import java.util.Arrays;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.AudioType;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackData;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class PlayYoutubeSlash extends SlashCommand {
    private PlayerManager pm;

    public PlayYoutubeSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "video", "Link or video name", true),
            new OptionData(OptionType.BOOLEAN, "force", "Force play", false)
        );
        this.pm = PlayerManager.get();
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String query = event.getOption("video").getAsString();
        Guild guild = event.getGuild();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();

        if(myChannel == null){
            event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
            return;
        }
        
        pm.loadItemOrdered(guild, query, new ResultHandler(event, false));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final SlashCommandEvent event;
        private final Guild guild;
        private final Member author;
        private final String args;
        private final boolean youtubeSearch;
        private final boolean force;
        private final TrackScheduler ts;
        
        private ResultHandler(SlashCommandEvent event, boolean youtubeSearch) {
            this.event = event;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.args = event.getOption("video").getAsString();
            this.youtubeSearch = youtubeSearch;
            this.force = event.getOption("force") != null && event.getOption("force").getAsBoolean();
            this.ts = pm.getGuildMusicManager(guild).getTrackScheduler();
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            track.setUserData(new TrackData(AudioType.AUDIO));
            ts.queue(track);
            if (ts.canPlay()) {
                ts.moveCursor(ts.getQueue().size(), true);
                ts.play();
            }

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());
            QueueHandler.sendQueueEmbed(event, guild);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

            if(youtubeSearch) {
                AudioTrack track = playlist.getTracks().get(0);
                track.setUserData(new TrackData(AudioType.AUDIO));
                int seconds = SafJNest.extractSeconds(args);
                if(seconds != -1)
                    track.setPosition(seconds * 1000);
                if(force) {
                    ts.addTrackToFront(track);
                    ts.play(ts.moveCursor(1), true);

                    guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());
                }
                else {
                    ts.queue(track);
                    if (ts.canPlay()) {
                        ts.moveCursor(ts.getQueue().size(), true);
                        ts.play();
                    }
                    guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());
                }
            }
            else {
                java.util.List<AudioTrack> tracks = playlist.getTracks();
                if (force) {
                    for(AudioTrack track : tracks) {
                        track.setUserData(new TrackData(AudioType.AUDIO));
                        ts.addTrackToFront(track);
                    }
                    ts.play(ts.moveCursor(1), true);
                    
                }
                else {
                    for(AudioTrack track : tracks) {
                        track.setUserData(new TrackData(AudioType.AUDIO));
                        pm.getGuildMusicManager(guild).getTrackScheduler().queue(track);
                    }
                    
                    if (ts.canPlay()) {
                        ts.moveCursor(ts.getQueue().size(), true);
                        ts.play();
                    }
                }
                guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());
                
                
            }
            QueueHandler.sendQueueEmbed(event, guild);
        }

        @Override
        public void noMatches() {
            if(!youtubeSearch) {
                pm.loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(event, true));
                return;
            }
            event.deferReply(true).addContent("No matches").queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            event.reply(throwable.getMessage());
        }
    }
}