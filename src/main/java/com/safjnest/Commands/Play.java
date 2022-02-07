package com.safjnest.Commands;

import java.awt.Color;
import java.time.Duration;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.safjnest.Utilities.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class Play extends Command {
    private JDA jda;
    private HashMap<String,String> tierOneLink;

    public Play(JDA jda, HashMap<String,String> tierOneLink){
        this.name = "play";
        this.aliases = new String[]{"nuovavita"};
        this.help = "il bot si connette e ti outplaya con le canzoni di mario";
        this.jda = jda;
        this.tierOneLink = tierOneLink;
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        MessageChannel channel = event.getChannel();
        EmbedBuilder eb = new EmbedBuilder();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioPlayerSendHandler audioPlayerSendHandler = new AudioPlayerSendHandler(player);
        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);

        playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
        playerManager.loadItem(commandArray[1], new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                trackScheduler.addQueue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                /*
                 * for (AudioTrack track : playlist.getTracks()) {
                 * trackScheduler.queue(track);
                 * }
                 */
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Canzone non trovata").queue();
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                // Notify the user that everything exploded
            }
        });
        player.playTrack(trackScheduler.getTrack());
        
        eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.setDescription(player.getPlayingTrack().getInfo().title);
        eb.setColor(new Color(255, 0, 0));
        eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
            channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
        eb.addField("Durata", getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(jda.getSelfUser().getName(), "https://github.com/SafJNest",jda.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rythem, questa e' perfezione cit. steve jobs", null);
        
        System.out.println("playing: " + player.getPlayingTrack().getIdentifier());
        channel.sendMessageEmbeds(eb.build()).queue();
	}

    private String getFormattedDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        String formattedTime = String.format("%02d", duration.toHoursPart()) 
                                            + ":" + String.format("%02d", duration.toMinutesPart()) 
                                            + ":" + String.format("%02d", duration.toSecondsPart()) 
                                            + "s";
        if(formattedTime.startsWith("00:"))
            formattedTime = formattedTime.substring(3);
        if(formattedTime.startsWith("00:"))
            formattedTime = formattedTime.substring(3);
        return formattedTime;
    }
}
