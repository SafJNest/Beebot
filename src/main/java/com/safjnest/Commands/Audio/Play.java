package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.util.HashMap;

import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TrackScheduler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.safjnest.Utilities.JSONReader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Play extends Command {
    private HashMap<String,String> tierOneLink;

    public Play(HashMap<String,String> tierOneLink){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.tierOneLink = tierOneLink;
    }

	@Override
	protected void execute(CommandEvent event) {
        String toPlay = event.getArgs();
        if(event.getMember().getVoiceState().getChannel() == null){
            event.reply("Non sei in un canale vocale.");
            return;
        }

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
        
        playerManager.loadItem(toPlay, new AudioLoadResultHandler() {
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
                trackScheduler.addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                event.reply(throwable.getMessage());
            }
        });
        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;
        eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.addField("Durata", SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rhythm, questa e' perfezione cit. steve jobs (probabilmente)", null);
        eb.setColor(new Color(255, 0, 0));
        eb.setDescription(player.getPlayingTrack().getInfo().title);
        eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        event.reply(eb.build());
        
        if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
            channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
	}
}