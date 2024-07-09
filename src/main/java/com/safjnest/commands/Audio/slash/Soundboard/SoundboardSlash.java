package com.safjnest.commands.Audio.slash.Soundboard;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class SoundboardSlash extends SlashCommand{

    public SoundboardSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();


        String father = this.getClass().getSimpleName().replace("Slash", "");

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new SoundboardCreateSlash(father), new SoundboardSelectSlash(father), new SoundboardAddSlash(father), new SoundboardRemoveSlash(father), new SoundboardDeleteSlash(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
        
        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }
}