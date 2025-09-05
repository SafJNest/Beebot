package com.safjnest.commands.audio;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.tts.TTSHandler;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

public class TTS extends SlashCommand{
    private PlayerManager pm;
    
    public final HashMap<String, Set<String>> voices;
    
    public TTS(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "text", "Text to be read", true),
            new OptionData(OptionType.STRING, "voice", "Reader's voice (also language)", false)
                .setAutoComplete(true)
        );

        commandData.setThings(this);

        this.pm = PlayerManager.get();
        this.voices = TTSVoices.getVoices();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String voice = null, language = null;
        String speech = event.getOption("text").getAsString();

        Guild guild = event.getGuild();
        GuildData guildData = GuildCache.getGuildOrPut(guild);
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        if(myChannel == null){
            event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
            return;
        }

        String defaultVoice = guildData.getVoice();
        String defaultLanguage = guildData.getLanguage();

        if(event.getOption("voice") != null) {
            String possibleVoice = event.getOption("voice").getAsString();
            for(String key : voices.keySet()) {
                if(voices.get(key).contains(possibleVoice)) {
                    language = key;
                    voice = possibleVoice;
                    break;
                }
            }
        }

        if(voice == null && defaultVoice != null) {
            voice = defaultVoice;
            language = defaultLanguage;
        }

        File file = new File("rsc" + File.separator + "tts");
        if(!file.exists())
            file.mkdirs();
 
        pm.loadItemOrdered(guild, TTSHandler.makeSpeechBytes(speech, voice, language), new ResultHandler(event, voice, defaultVoice, language));
    }

    @Override
    protected void execute(CommandEvent event) {
        String voice = null, language = null;
        String speech = event.getArgs();

        Guild guild = event.getGuild();
        GuildData guildData = GuildCache.getGuildOrPut(guild.getId());
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

        pm.loadItemOrdered(guild, TTSHandler.makeSpeechBytes(speech, voice, language), new ResultHandler(event, voice, defaultVoice, language));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final SlashCommandEvent slashEvent;
        private final CommandEvent commandEvent;
        private final Guild guild;
        private final Member author;
        private final String voice;
        private final String defaultVoice;
        private final String language;

        private ResultHandler(SlashCommandEvent event, String voice, String defaultVoice, String language) {
            this.slashEvent = event;
            this.commandEvent = null;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.voice = voice;
            this.defaultVoice = defaultVoice;
            this.language = language;
        }

        private ResultHandler(CommandEvent event, String voice, String defaultVoice, String language) {
            this.slashEvent = null;
            this.commandEvent = event;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.voice = voice;
            this.defaultVoice = defaultVoice;
            this.language = language;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            pm.getGuildMusicManager(guild).getTrackScheduler().play(track, true);

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

            EmbedBuilder eb = new EmbedBuilder();
        
            eb.setTitle("Playing now:");
            eb.setColor(Bot.getColor());


            if (commandEvent != null) {
                eb.setDescription(commandEvent.getArgs());
                eb.setThumbnail(commandEvent.getSelfUser().getAvatarUrl());
                eb.setAuthor(commandEvent.getAuthor().getName(), "https://github.com/SafJNest", commandEvent.getAuthor().getAvatarUrl());
            }
            else {
                eb.setDescription(slashEvent.getOption("text").getAsString());
                eb.setThumbnail(slashEvent.getJDA().getSelfUser().getAvatarUrl());
                eb.setAuthor(slashEvent.getMember().getEffectiveName(), "https://github.com/SafJNest", slashEvent.getMember().getAvatarUrl());
            }
            eb.addField("Lenght", SafJNest.getFormattedDuration(track.getInfo().length),true);
            eb.addField("Language", language, true);
            eb.addBlankField(true);
            eb.addField("Voice", voice, true);
            eb.addField("Default voice", (defaultVoice == null ? "Not set" : defaultVoice), true);
            eb.addBlankField(true);

            reply(eb);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {}

        @Override
        public void noMatches() {
            reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            reply(throwable.getMessage());
        }

        private void reply(String message) {
            if(slashEvent != null)
                slashEvent.deferReply(false).addContent(message).queue();
            else
                commandEvent.reply(message);
        }

        private void reply(EmbedBuilder eb) {
            if(slashEvent != null)
                slashEvent.deferReply(false).addEmbeds(eb.build()).queue();
            else
                commandEvent.reply(eb.build());
        }
    }
}
