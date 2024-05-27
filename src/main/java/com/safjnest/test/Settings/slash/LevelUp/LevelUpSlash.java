package com.safjnest.commands.Settings.slash.LevelUp;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;

public class LevelUpSlash extends SlashCommand {

    private GuildDataHandler gs;

    public LevelUpSlash(GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        String father = this.getClass().getSimpleName().replace("Slash", "");

        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.category = new Category("Settings");
        this.help = "json";
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new LevelUpPreviewSlash(father), new LevelUpTextSlash(father), new LevelUpToggleSlash(gs, father), new LevelUpChannelToggleSlash(father, gs), new LevelUpModifierSlash(father, gs), new LevelUpUpdateTimeSlash(father, gs));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);                                 
        
        this.gs = gs;
    }

    @Override
    protected void execute(SlashCommandEvent event) { 
        gs.doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed();
    }
    
}
