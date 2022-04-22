package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.Utilities.TrackScheduler;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import org.apache.commons.io.FileUtils;

import net.dv8tion.jda.api.EmbedBuilder;

import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;

public class PlaySound extends Command{
    AmazonS3 s3Client;
    S3Object fullObject = null;
    String name;

    public PlaySound(AmazonS3 s3Client){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.s3Client = s3Client;
    }

    @Override
    protected void execute(CommandEvent event) {
        if((name = event.getArgs()) == ""){
            event.reply("il nome idiota");
            return;
        }
        //TODO fix | deletare il file vecchio ogni ps bene
        for (File file : new java.io.File("rsc" + File.separator + "SoundBoard").listFiles())
            file.delete();

        try {
            System.out.println("Downloading an object");
            fullObject = s3Client.getObject(new GetObjectRequest("thebeebox", name));
            System.out.println("Content-Type: " + fullObject.getObjectMetadata().getContentType());
            S3ObjectInputStream s3is = fullObject.getObjectContent();
            FileUtils.copyInputStreamToFile(s3is, new File("rsc" + File.separator + "SoundBoard"+ File.separator + name + ".mp3"));
            s3is.close();
        } catch (AmazonClientException | IOException ace) {
            ace.printStackTrace();
        }
        
        name = "rsc" + File.separator + "SoundBoard" + File.separator + name + ".mp3";
        
        MessageChannel channel = event.getChannel();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioPlayerSendHandler audioPlayerSendHandler = new AudioPlayerSendHandler(player);
        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        playerManager.loadItem(name, new AudioLoadResultHandler() {
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
                System.out.println("error: " + throwable.getMessage());
            }
        });

        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.addField("Durata", SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rhythm, questa e' perfezione cit. steve jobs (probabilmente)", null);
        Mp3File mp = SoundBoard.getMp3FileByName(player.getPlayingTrack().getInfo().title);
        eb.setColor(new Color(0, 255, 255));
        eb.setDescription(event.getArgs());
        eb.addField("Autore", mp.getId3v2Tag().getAlbumArtist(), true);
        eb.addField("Album", mp.getId3v2Tag().getAlbum(), true);
        String img = "mp3.png";
        switch (mp.getId3v2Tag().getAlbumArtist()) {
            case "merio":img = "epria.jpg";break;case "dirix":img = "dirix.jpg";break;case "teros":img = "zucca.jpg";break;case "herox":img = "herox.jpg";break;case "bomber":img = "arcus.jpg";break;case "ilyas":img = "maluma.PNG";break;case "pyke":img = "pyke.jpg";break;case "thresh":img = "thresh.jpg";break;case "blitzcrank":img = "blitz.png";break;case "bard":img = "bard.png";break;case "nautilus":img = "nautilus.png";break;case "fiddle":img = "fid.jpg";break;case "pantanichi":img = "panta.jpg";break;case "sunyx":img = "sun.jpg";break;case "gskianto":img = "gk.png";break;case "jhin":img = "jhin.jpg";break;case "yone":img = "yone.jpg";break;case "yasuo":img = "yasuo.jpg";break;
        }
        File file = new File("rsc" + File.separator + "rsc" + File.separator + "img" + File.separator+ img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
            .addFile(file, img)
            .queue();
    }
}
