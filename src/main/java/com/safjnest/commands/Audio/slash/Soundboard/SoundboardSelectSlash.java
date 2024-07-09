package com.safjnest.commands.Audio.slash.Soundboard;


import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import com.safjnest.core.audio.SoundHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;


import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 2.1
 */
public class SoundboardSelectSlash extends SlashCommand{

    public SoundboardSelectSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Soundboard to play", true)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String soundboardID = event.getOption("name").getAsString();
        SoundHandler.composeSoundboard(event, soundboardID).queue();
        
    }
}