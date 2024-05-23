package com.safjnest.SlashCommands.Audio;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.App;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TTSHandler;
import com.safjnest.Utilities.Audio.TTSVoices;
import com.safjnest.Utilities.Guild.GuildData;
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

public class TTSSlash extends SlashCommand{
    private TTSHandler tts;
    private PlayerManager pm;
    
    public final HashMap<String, Set<String>> voices;
    
    public TTSSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "text", "Text to be read", true),
            new OptionData(OptionType.STRING, "voice", "Reader's voice (also language)", false)
                .setAutoComplete(true)
        );

        this.tts = App.getTTS();
        this.pm = PlayerManager.get();
        this.voices = TTSVoices.getVoices();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String voice = null, language = null;
        String speech = event.getOption("text").getAsString();

        Guild guild = event.getGuild();
        GuildData guildData = Bot.getGuildData(guild.getId());
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
            System.out.println(possibleVoice);
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

        tts.makeSpeech(speech, event.getMember().getEffectiveName(), voice, language);
        
        String nameFile = "rsc" + File.separator + "tts" + File.separator + event.getMember().getEffectiveName() + ".mp3";
        
        pm.loadItemOrdered(guild, nameFile, new ResultHandler(event, voice, defaultVoice, language));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final SlashCommandEvent event;
        private final Guild guild;
        private final Member author;
        private final String voice;
        private final String defaultVoice;
        private final String language;

        private ResultHandler(SlashCommandEvent event, String voice, String defaultVoice, String language) {
            this.event = event;
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
            eb.setDescription(event.getOption("text").getAsString());
            eb.setColor(Bot.getColor());
            eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
            eb.setAuthor(event.getMember().getEffectiveName(), "https://github.com/SafJNest", event.getMember().getAvatarUrl());
            
            eb.addField("Lenght", SafJNest.getFormattedDuration(track.getInfo().length),true);
            eb.addField("Language", language, true);
            eb.addBlankField(true);
            eb.addField("Voice", voice, true);
            eb.addField("Default voice", (defaultVoice == null ? "Not set" : defaultVoice), true);
            eb.addBlankField(true);

            event.deferReply(false).addEmbeds(eb.build()).queue();
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
