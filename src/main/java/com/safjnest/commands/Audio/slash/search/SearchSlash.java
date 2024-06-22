package com.safjnest.commands.Audio.slash.search;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class SearchSlash extends SlashCommand{

    public SearchSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        String father = this.getClass().getSimpleName().replace("Slash", "");

        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.help = new CommandsLoader().getString(this.name, "help");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new SearchYoutubeSlash(father), new SearchSoundSlash(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }
}