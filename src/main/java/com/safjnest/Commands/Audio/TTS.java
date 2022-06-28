//TODO RIFARE QUESTA CLASSE DI MERDA INGUARADFBILE :D
package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;
import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.TrackScheduler;
import com.safjnest.Utilities.tts.Languages;
import com.safjnest.Utilities.tts.Voices;
import com.safjnest.Utilities.AudioHandler;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TTSHandler;
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

public class TTS extends Command{
    String speech;
    TTSHandler tts;
    private static final Set<String> ita = Set.of("Pietro", "Mia", "Bria");
    private static final HashMap<String, Set<String>> voices = new HashMap<String, Set<String>>();
    public TTS(TTSHandler tts){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.tts = tts;
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_SaudiArabia.id, Set.of(Voices.Arabic_SaudiArabia.array));
        voices.put(Voices.Bulgarian.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Catalan.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Catalan.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Chinese_China.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Chinese_HongKong.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Chinese_Taiwan.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Croatian.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Czech.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Danish.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Dutch_Belgium.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Dutch_Netherlands.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_Australia.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_Canada.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_GreatBritain.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_India.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_Ireland.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.English_UnitedStates.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));

    
    }

    @Override
    protected void execute(CommandEvent event) {
        if((speech = event.getArgs()) == ""){
            event.reply("scrivi qualcosa pezzo diemrdqa");
            return;
        }
        File file = new File("rsc" + File.separator + "tts");
        if(!file.exists())
            file.mkdirs();
        if(ita.contains(event.getArgs().split(" ")[0])){
            speech = event.getArgs().substring(event.getArgs().indexOf(" "));
            tts.makeSpeech(speech, event.getAuthor().getName(), event.getArgs().split(" ")[0]);
        }else{
            tts.makeSpeech(event.getArgs(), event.getAuthor().getName());
        }
        String nameFile = "rsc" + File.separator + "tts" + File.separator + event.getAuthor().getName() + ".mp3";
        MessageChannel channel = event.getChannel();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioHandler audioPlayerSendHandler = new AudioHandler(player);

        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        playerManager.loadItem(nameFile, new AudioLoadResultHandler() {
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
        eb.addField("Durata",SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length),true);
        eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest",event.getAuthor().getAvatarUrl());
        eb.setFooter("*Questo non e' SoundFx, questa e' perfezione cit. steve jobs (probabilmente)", null);

        eb.setDescription(event.getArgs());
        eb.addField("Autore", event.getAuthor().getName(), true);

        String img = "jelly.png";
        eb.setColor(new Color(255, 0, 0));
            

        File path = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
            .addFile(path, img)
            .queue();
        
    }
}
