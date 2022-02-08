package com.safjnest.Commands;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.safjnest.Utilities.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
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
    private static File folder = new File("SoundBoard");

    public Play(JDA jda, HashMap<String,String> tierOneLink){
        this.name = "play";
        this.aliases = new String[]{"nuovavita", "p", "ehiprimofreestyle2020nuovavitastoincamerettaascrivereco''namatitaco''steparolelascenavienedemolitaelavincoioquestacazzodipartitaspaccolatracciasifra,tispaccolafacciatuquanorappisembriarrugginitoletuerimecosìsquallidecherimangobasitodicitantocheiltuorapsfondamisachecontuttelecavolatechespariaffondailmioraptisfondailtuosprofonda...sehfrailtuosprofonda...ehi..okaycheconquesterimet'hogiàrottoilculoet'assicurochepertenonc'èfuturoquinditecensuroesevuoifareunacosabonabeveteercianuroteepornhubsembratecicciocolsuopagurosentel'attaccochetesferrotipiacepredereilferronell'anodatizicometizianoferroalloravuoipurelafamatisputoinfacciatipolamaiovado,sonoilredelrapancora,chiama."};
        this.help = "il bot si connette e ti outplaya con le canzoni di mario";
        this.jda = jda;
        this.tierOneLink = tierOneLink;
    }

	@Override
	protected void execute(CommandEvent event) {
        boolean isYoutube = false;
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        if(commandArray[1].contains("www.youtube.com"))
            isYoutube = true;
        else{
                commandArray[1] = SoundBoard.containsFile(commandArray[1]);
                System.out.println("nome del faker " + commandArray[1]);
                if(commandArray[1] == null){
                    event.reply("Suono non trovato");
                    return;
                }
                commandArray[1] = "SoundBoard\\" + commandArray[1]; 
                System.out.println("nome del faker 2 " + commandArray[1]);
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
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        
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
                System.out.println("error faker " + throwable.getMessage());
            }
        });
        System.out.println("bossetti " + playerManager.toString());
        player.playTrack(trackScheduler.getTrack());
        
        eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.setDescription(player.getPlayingTrack().getInfo().title);
        if(isYoutube)
            eb.setColor(new Color(255, 0, 0));
        else
            eb.setColor(new Color(18, 223, 227));
        
        if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
            channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
        eb.addField("Durata", SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(jda.getSelfUser().getName(), "https://github.com/SafJNest",jda.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rythem, questa e' perfezione cit. steve jobs", null);
        if(isYoutube){
            eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
            event.reply(eb.build());
        }else{
            File file = new File("img\\mp3.png");
            eb.setThumbnail("attachment://mp3.png");
             channel.sendMessageEmbeds(eb.build())
                        .addFile(file, "mp3.png")
                        .queue();
        }
	}

}
