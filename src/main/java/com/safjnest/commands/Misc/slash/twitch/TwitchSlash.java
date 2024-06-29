package com.safjnest.commands.Misc.slash.twitch;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;

public class TwitchSlash extends SlashCommand{

    public TwitchSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        String father = this.getClass().getSimpleName().replace("Slash", "");

        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.help = "json";
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new TwitchLinkSlash(father), new TwitchUnlinkSlash(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);    
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
