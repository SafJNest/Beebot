//TODO RIFARE QUESTA CLASSE DI MERDA INGUARADFBILE :D | mmh... dontt know about that chief
package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;
import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.TrackScheduler;
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
        voices.put(Voices.Bulgarian.id, Set.of(Voices.Bulgarian.array));
        voices.put(Voices.Catalan.id, Set.of(Voices.Catalan.array));
        voices.put(Voices.Chinese_China.id, Set.of(Voices.Chinese_China.array));
        voices.put(Voices.Chinese_HongKong.id, Set.of(Voices.Chinese_HongKong.array));
        voices.put(Voices.Chinese_Taiwan.id, Set.of(Voices.Chinese_Taiwan.array));
        voices.put(Voices.Croatian.id, Set.of(Voices.Croatian.array));
        voices.put(Voices.Czech.id, Set.of(Voices.Czech.array));
        voices.put(Voices.Danish.id, Set.of(Voices.Danish.array));
        voices.put(Voices.Dutch_Belgium.id, Set.of(Voices.Dutch_Belgium.array));
        voices.put(Voices.Dutch_Netherlands.id, Set.of(Voices.Dutch_Netherlands.array));
        voices.put(Voices.English_Australia.id, Set.of(Voices.English_Australia.array));
        voices.put(Voices.English_Canada.id, Set.of(Voices.English_Canada.array));
        voices.put(Voices.English_GreatBritain.id, Set.of(Voices.English_GreatBritain.array));
        voices.put(Voices.English_India.id, Set.of(Voices.English_India.array));
        voices.put(Voices.English_Ireland.id, Set.of(Voices.English_Ireland.array));
        voices.put(Voices.English_UnitedStates.id, Set.of(Voices.English_UnitedStates.array));
        voices.put(Voices.Finnish.id, Set.of(Voices.Finnish.array));
        voices.put(Voices.French_Canada.id, Set.of(Voices.French_Canada.array));
        voices.put(Voices.French_France.id, Set.of(Voices.French_France.array));
        voices.put(Voices.French_Switzerland.id, Set.of(Voices.French_Switzerland.array));
        voices.put(Voices.German_Austria.id, Set.of(Voices.German_Austria.array));
        voices.put(Voices.German_Germany.id, Set.of(Voices.German_Germany.array));
        voices.put(Voices.German_Switzerland.id, Set.of(Voices.German_Switzerland.array));
        voices.put(Voices.Greek.id, Set.of(Voices.Greek.array));
        voices.put(Voices.Hebrew.id, Set.of(Voices.Hebrew.array));
        voices.put(Voices.Hindi.id, Set.of(Voices.Hindi.array));
        voices.put(Voices.Hungarian.id, Set.of(Voices.Hungarian.array));
        voices.put(Voices.Indonesian.id, Set.of(Voices.Indonesian.array));
        voices.put(Voices.Italian.id, Set.of(Voices.Italian.array));
        voices.put(Voices.Japanese.id, Set.of(Voices.Japanese.array));
        voices.put(Voices.Korean.id, Set.of(Voices.Korean.array));
        voices.put(Voices.Malay.id, Set.of(Voices.Malay.array));
        voices.put(Voices.Norwegian.id, Set.of(Voices.Norwegian.array));
        voices.put(Voices.Polish.id, Set.of(Voices.Polish.array));
        voices.put(Voices.Portuguese_Brazil.id, Set.of(Voices.Portuguese_Brazil.array));
        voices.put(Voices.Portuguese_Portugal.id, Set.of(Voices.Portuguese_Portugal.array));
        voices.put(Voices.Romanian.id, Set.of(Voices.Romanian.array));
        voices.put(Voices.Russian.id, Set.of(Voices.Russian.array));
        voices.put(Voices.Slovak.id, Set.of(Voices.Slovak.array));
        voices.put(Voices.Spanish_Mexico.id, Set.of(Voices.Spanish_Mexico.array));
        voices.put(Voices.Spanish_Spain.id, Set.of(Voices.Spanish_Spain.array));
        voices.put(Voices.Swedish.id, Set.of(Voices.Swedish.array));
        voices.put(Voices.Tamil.id, Set.of(Voices.Tamil.array));
        voices.put(Voices.Thai.id, Set.of(Voices.Thai.array));
        voices.put(Voices.Turkish.id, Set.of(Voices.Turkish.array));
        voices.put(Voices.Vietnamese.id, Set.of(Voices.Vietnamese.array));

    
    }

    @Override
    protected void execute(CommandEvent event) {
        String language = "ita";
        String voice = "Pietro";
        MessageChannel channel = event.getChannel();
        if((speech = event.getArgs()) == ""){
            event.reply("scrivi qualcosa pezzo diemrdqa");
            return;
        }else if (event.getArgs().split(" ")[0].equalsIgnoreCase("list")){
            String lang = "";
            for(String key : voices.keySet()){
                lang += "**"+ key.toUpperCase() +"**" + ":\n";
                for(String s : voices.get(key)){
                    lang += s + " - ";
                }
                lang = lang.substring(0, lang.length()-3) + "\n";
            }
            event.reply(lang);
            return;
        }
        File file = new File("rsc" + File.separator + "tts");
        if(!file.exists())
            file.mkdirs();     
        for(String key : voices.keySet()){
            if(voices.get(key).contains(event.getArgs().split(" ")[0])){
                language = key;
                voice = event.getArgs().split(" ")[0];
                speech = event.getArgs().substring(event.getArgs().indexOf(" "));
            }
        }
        tts.makeSpeech(speech, event.getAuthor().getName(), voice, language);
        
        String nameFile = "rsc" + File.separator + "tts" + File.separator + event.getAuthor().getName() + ".mp3";
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
        eb.addField("Lingua", language, true);
        eb.addField("Voce", voice, true);
        String img = "jelly.png";
        eb.setColor(new Color(255, 0, 0));
            

        File path = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
            .addFile(path, img)
            .queue();
        
    }
}
