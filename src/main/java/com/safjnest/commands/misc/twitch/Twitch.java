package com.safjnest.commands.misc.twitch;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class Twitch extends SlashCommand{

    public Twitch(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();


        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new TwitchLink(father), new TwitchUnlink(father), new TwitchMenu(father), new TwitchUser(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);    

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
