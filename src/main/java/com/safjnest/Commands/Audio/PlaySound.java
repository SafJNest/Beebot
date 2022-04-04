package com.safjnest.Commands.Audio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.FileListener;

import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.safjnest.Utilities.TrackScheduler;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

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
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;


public class PlaySound extends Command{
    AmazonS3 s3Client;
    S3Object fullObject = null;
    String name;

    public PlaySound(AmazonS3 s3Client){
        this.name = "playsound";
        this.aliases = new String[]{"ps", "playsos"};
        this.category = new Category("Audio");
        this.arguments = "[playsound] [nome del suono, senza specificare il formato]";
        this.s3Client = s3Client;
    }

    @Override
    protected void execute(CommandEvent event) {
        if((name = event.getArgs()) == ""){
            event.reply("il nome idiota");
            return;
        }
        
        try {
            System.out.println("Downloading an object");
            fullObject = s3Client.getObject(new GetObjectRequest("thebeebox", name));
            System.out.println("Content-Type: " + fullObject.getObjectMetadata().getContentType());
            S3ObjectInputStream s3is = fullObject.getObjectContent();
            FileOutputStream fos = new FileOutputStream(new File("SoundBoard"+ File.separator + name + ".mp3"));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
        } catch (AmazonClientException | IOException ace) {
            ace.printStackTrace();
        }
        
        name = "SoundBoard" + File.separator + name + ".mp3"; 
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
                System.out.println("error faker " + throwable.getMessage());
            }
        });
        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;
    }
}
