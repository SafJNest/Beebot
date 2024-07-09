package com.safjnest.commands.Settings.slash.Boost;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class BoostSlash extends SlashCommand {

    public BoostSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();


        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new BoostChannelSlash(father), new BoostCreateSlash(father), new BoostDeleteSlash(father), new BoostPreviewSlash(father), new BoostTextSlash(father), new BoostToggleSlash(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);

        commandData.setThings(this);                          
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        
    }
    
}
