package com.safjnest.commands.audio.soundboard;

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
public class Soundboard extends SlashCommand{

    public Soundboard(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();


        String father = this.getClass().getSimpleName().replace("Slash", "");

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new SoundboardCreate(father), new SoundboardPlay(father), new SoundboardAdd(father), new SoundboardRemove(father), new SoundboardDelete(father), new SoundboardThumbnail(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
        
        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }
}