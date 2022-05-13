//TODO RIFARE QUESTA CLASSE DI MERDA INGUARADFBILE :D
package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;

import com.amazonaws.services.s3.model.S3Object;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.TrackScheduler;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;


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
    AwsS3 s3Client;
    String name;

    public PlaySound(AwsS3 s3Client){
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
        File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        if(!soundBoard.exists())
            soundBoard.mkdirs();
        //TODO fix | deletare il file vecchio ogni ps bene
        for (File file : soundBoard.listFiles())
            file.delete();

        S3Object sound = s3Client.downloadFile(name, event);
        String extension = SoundBoard.getExtension(name);
        if(extension == null){
            event.reply("il file non esiste");
            return;
        }
        name = "rsc" + File.separator + "SoundBoard" + File.separator + name +"."+ extension;
        
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
        eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest",event.getAuthor().getAvatarUrl());
        eb.setFooter("*Questo non e' SoundFx, questa e' perfezione cit. steve jobs (probabilmente)", null);
        //Mp3File mp = SoundBoard.getMp3FileByName(player.getPlayingTrack().getInfo().title);
        eb.setColor(new Color(0, 255, 255));
        eb.setDescription(event.getArgs());
        eb.addField("Autore", event.getJDA().getUserById(sound.getObjectMetadata().getUserMetaDataOf("author")).getName(), true);
        eb.addField("Guild", event.getJDA().getGuildById(sound.getObjectMetadata().getUserMetaDataOf("guild")).getName(), true);
        String img = "mp3.png";
        if(extension.equals("opus"))
            img = "jelly.png";
        /*
        switch (mp.getId3v2Tag().getAlbumArtist()) {
            case "merio":img = "epria.jpg";break;case "dirix":img = "dirix.jpg";break;case "teros":img = "zucca.jpg";break;case "herox":img = "herox.jpg";break;case "bomber":img = "arcus.jpg";break;case "ilyas":img = "maluma.PNG";break;case "pyke":img = "pyke.jpg";break;case "thresh":img = "thresh.jpg";break;case "blitzcrank":img = "blitz.png";break;case "bard":img = "bard.png";break;case "nautilus":img = "nautilus.png";break;case "fiddle":img = "fid.jpg";break;case "pantanichi":img = "panta.jpg";break;case "sunyx":img = "sun.jpg";break;case "gskianto":img = "gk.png";break;case "jhin":img = "jhin.jpg";break;case "yone":img = "yone.jpg";break;case "yasuo":img = "yasuo.jpg";break;
        }
        */
        File file = new File("rsc" + File.separator + "img" + File.separator+ img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
            .addFile(file, img)
            .queue();
        
    }
}
