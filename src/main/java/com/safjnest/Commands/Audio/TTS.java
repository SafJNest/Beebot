package com.safjnest.Commands.Audio;

import java.io.File;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.App;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.AudioType;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TTSHandler;
import com.safjnest.Utilities.Audio.TTSVoices;
import com.safjnest.Utilities.Audio.TrackData;
import com.safjnest.Utilities.Guild.GuildData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class TTS extends Command{
    private TTSHandler tts;
    private PlayerManager pm;
    
    public final HashMap<String, Set<String>> voices;
    
    public TTS(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");

        this.tts = App.getTTS();
        this.pm = PlayerManager.get();
        this.voices = TTSVoices.getVoices();
    }

    @Override
    protected void execute(CommandEvent event) {
        String voice = null, language = null;
        String speech = event.getArgs();

        Guild guild = event.getGuild();
        GuildData guildData = Bot.getGuildData(guild.getId());
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();

        if(speech.equals("")) {
            event.reply("Write the text you want to turn into speech (or list to get the list of voices).");
            return;
        }

        String firstWord = speech.split(" ", 2)[0];

        if(firstWord.equalsIgnoreCase("list")) {
            EmbedBuilder eb = new EmbedBuilder();

            eb.setTitle("Available languages:");
            eb.setThumbnail(event.getSelfUser().getAvatarUrl());
            eb.setColor(Bot.getColor());

            for(Entry<String, Set<String>> entry : voices.entrySet()){
                String lang = "**" + entry.getKey().toUpperCase() + "**:\n";
                StringBuilder voiceString = new StringBuilder();
                for(String s : entry.getValue())
                    voiceString.append(s).append(" - ");
                voiceString.setLength(voiceString.length() - 3);
                eb.addField(lang.toString(), voiceString.toString(), true);
            }
            
            event.reply(eb.build());
            return;
        }

        if(myChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }

        String defaultVoice = guildData.getVoice();
        String defaultLanguage = guildData.getLanguage();

        for(String key : voices.keySet()){
            if(voices.get(key).contains(firstWord)) {
                language = key;
                voice = firstWord;
                break;
            }
        }

        if(voice != null){
            speech = speech.split(" ", 2)[1];
        }
        else if (defaultVoice != null) {
            voice = defaultVoice;
            language = defaultLanguage;
            
        }

        File file = new File("rsc" + File.separator + "tts");
        if(!file.exists())
            file.mkdirs();

        tts.makeSpeech(speech, event.getAuthor().getName(), voice, language);
        
        String ttsFileName = "rsc" + File.separator + "tts" + File.separator + event.getAuthor().getName() + ".mp3";

        pm.loadItemOrdered(guild, ttsFileName, new ResultHandler(event, voice, defaultVoice, language));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        private final Guild guild;
        private final Member author;
        private final String voice;
        private final String defaultVoice;
        private final String language;

        private ResultHandler(CommandEvent event, String voice, String defaultVoice, String language) {
            this.event = event;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.voice = voice;
            this.defaultVoice = defaultVoice;
            this.language = language;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            track.setUserData(new TrackData(AudioType.SOUND));
            pm.getGuildMusicManager(guild).getTrackScheduler().play(track, true);

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

            EmbedBuilder eb = new EmbedBuilder();
        
            eb.setTitle("Playing now:");
            eb.setDescription(event.getArgs());
            eb.setColor(Bot.getColor());
            eb.setThumbnail(event.getSelfUser().getAvatarUrl());
            eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest", event.getAuthor().getAvatarUrl());
            
            eb.addField("Lenght", SafJNest.getFormattedDuration(track.getInfo().length),true);
            eb.addField("Language", language, true);
            eb.addBlankField(true);
            eb.addField("Voice", voice, true);
            eb.addField("Default voice", (defaultVoice == null ? "Not set" : defaultVoice), true);
            eb.addBlankField(true);

            event.reply(eb.build());
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {}

        @Override
        public void noMatches() {
            event.reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            event.reply(throwable.getMessage());
        }
    }
}
