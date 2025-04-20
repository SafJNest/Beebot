package com.safjnest.commands.settings;

import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import com.safjnest.core.cache.managers.GuildCache;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class Voice extends SlashCommand {

    public Voice() {
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
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String voice = event.getOption("voice").getAsString();
        if(voice == null) {
            event.deferReply(true).addContent("Voice not found").queue();
            return;
        }

        if (!GuildCache.getGuildOrPut(event.getGuild().getId()).setVoice(voice)) {
            event.deferReply(true).addContent("There was an error while changing the voice.").queue();
            return;
        }

        event.deferReply(false).addContent("Voice set to " + voice).queue();
    }
}