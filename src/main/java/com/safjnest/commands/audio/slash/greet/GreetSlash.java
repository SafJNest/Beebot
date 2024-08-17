package com.safjnest.commands.audio.slash.greet;

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
public class GreetSlash extends SlashCommand{

    public GreetSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();



        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new GreetSetSlash(father), new GreetUnsetSlash(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }

    

    

    
   
}
