package com.safjnest.commands.Settings.slash.Welcome;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class WelcomeSlash extends SlashCommand{

    public WelcomeSlash(GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new WelcomeChannelSlash(father), new WelcomeCreateSlash(father), new WelcomeDeleteSlash(father), new WelcomePreviewSlash(father), new WelcomeTextSlash(father), new WelcomeToggleSlash(father), new WelcomeAddRole(father), new WelcomeRemoveRole(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);

        commandData.setThings(this);                               
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}