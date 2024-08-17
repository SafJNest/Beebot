package com.safjnest.commands.settings.slash;

import com.safjnest.core.Bot;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class VoiceSlash extends SlashCommand {
    private final HashMap<String, Set<String>> voices;

    public VoiceSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "voice", "Voice name", true)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
        
        this.voices = TTSVoices.getVoices();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String voice= null, language = null;


        voice = event.getOption("voice").getAsString();
        for(String key : voices.keySet()) {
            if(voices.get(key).contains(voice)) {
                language = key;
                break;
            }
        }
        if(voice == null) {
            event.deferReply(true).addContent("Voice not found").queue();
            return;
        }

        if (!Bot.getGuildData(event.getGuild().getId()).setVoice(voice, language)) {
            event.deferReply(true).addContent("There was an error while changing the voice.").queue();
            return;
        }

        event.deferReply(false).addContent("Voice set to " + voice + " (" + language + ")").queue();
    }
}