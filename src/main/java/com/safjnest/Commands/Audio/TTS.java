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
import com.safjnest.Utilities.PostgreSQL;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TTSHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.utils.FileUpload;

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
    PostgreSQL sql;
    public static final HashMap<String, Set<String>> voices = new HashMap<String, Set<String>>();
    public TTS(TTSHandler tts, PostgreSQL sql){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.tts = tts;
        this.sql = sql;
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Chinese_China.id, Set.of(Voices.Chinese_China.array));
        voices.put(Voices.Dutch_Netherlands.id, Set.of(Voices.Dutch_Netherlands.array));
        voices.put(Voices.English_GreatBritain.id, Set.of(Voices.English_GreatBritain.array));
        voices.put(Voices.English_India.id, Set.of(Voices.English_India.array));
        voices.put(Voices.English_UnitedStates.id, Set.of(Voices.English_UnitedStates.array));
        voices.put(Voices.French_France.id, Set.of(Voices.French_France.array));
        voices.put(Voices.German_Germany.id, Set.of(Voices.German_Germany.array));
        voices.put(Voices.Greek.id, Set.of(Voices.Greek.array));
        voices.put(Voices.Italian.id, Set.of(Voices.Italian.array));
        voices.put(Voices.Japanese.id, Set.of(Voices.Japanese.array));
        voices.put(Voices.Korean.id, Set.of(Voices.Korean.array));
        voices.put(Voices.Polish.id, Set.of(Voices.Polish.array));
        voices.put(Voices.Portuguese_Portugal.id, Set.of(Voices.Portuguese_Portugal.array));
        voices.put(Voices.Romanian.id, Set.of(Voices.Romanian.array));
        voices.put(Voices.Russian.id, Set.of(Voices.Russian.array));
        voices.put(Voices.Swedish.id, Set.of(Voices.Swedish.array));
        voices.put(Voices.Spanish_Spain.id, Set.of(Voices.Spanish_Spain.array));

    
    }

    @Override
    protected void execute(CommandEvent event) {
        String language = "it-it";
        String voice = "keria";
        MessageChannel channel = event.getChannel();
        EmbedBuilder eb = null;
        if((speech = event.getArgs()) == ""){
            event.reply("Write somthing you want the bot to say");
            return;
        }else if (event.getArgs().split(" ")[0].equalsIgnoreCase("list")){
            eb = new EmbedBuilder();
            eb.setTitle("Available languages:");
            eb.setColor(new Color(255, 196, 0));
            String lang = "";
            String voiceString = "";
            for(String key : voices.keySet()){
                lang += "**"+ key.toUpperCase() +"**" + ":\n";
                for(String s : voices.get(key)){
                    voiceString += s + " - ";
                }
                eb.addField(lang, voiceString, true);
                lang = "";
                voiceString = "";
            }
            eb.setThumbnail(event.getSelfUser().getAvatarUrl());
            event.reply(eb.build());
            return;
        }
        File file = new File("rsc" + File.separator + "tts");
        if(!file.exists())
            file.mkdirs();
        for(String key : voices.keySet()){
            if(voices.get(key).contains(event.getArgs().split(" ")[0])){
                language = key;
                voice = event.getArgs().split(" ")[0];
            }
        }
        String query = "SELECT name_tts FROM tts_guilds WHERE discord_id = '" + event.getGuild().getId() + "';";
        if(!voice.equals("keria")){
            speech = event.getArgs().substring(event.getArgs().indexOf(" "));
        }
        else if(sql.getString(query, "name_tts") != null){
            voice = sql.getString(query, "name_tts");
            query = "SELECT language_tts FROM tts_guilds WHERE discord_id = '" + event.getGuild().getId() + "';"; 
            language = sql.getString(query, "language_tts");
            speech = event.getArgs();
        }
        else{
            voice = "Mia";  
            speech = event.getArgs();
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
                channel.sendMessage("Not found").queue();
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
        
        eb = new EmbedBuilder();
        eb.setTitle("Playing now:");
        eb.addField("Lenght",SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length),true);
        eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest",event.getAuthor().getAvatarUrl());
        eb.setFooter("*This is not SoundFx, this is much worse cit. steve jobs (probably)", null); //Questo non e' SoundFx, questa e' perfezione cit. steve jobs (probabilmente)

        eb.setDescription(event.getArgs());
        eb.addField("Language", language, true);
        eb.addField("Voice", voice, true);
        String img = "jelly.png";
        eb.setColor(new Color(255, 196, 0));
            

        File path = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
            .addFiles(FileUpload.fromData(path))
            .queue();
        
    }
}
