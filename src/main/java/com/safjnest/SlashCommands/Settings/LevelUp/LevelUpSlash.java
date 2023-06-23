package com.safjnest.SlashCommands.Settings.LevelUp;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.Guild.GuildSettings;

public class LevelUpSlash extends SlashCommand {

    private GuildSettings gs;

    public LevelUpSlash(GuildSettings gs){
        this.gs = gs;
        this.name = "levelup";//this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        //this.aliases = new CommandsLoader().getArray(this.name, "alias");
        //this.help = new CommandsLoader().getString(this.name, "help");
        //this.cooldown = new CommandsLoader().getCooldown(this.name);
        //this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        //this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.category = new Category("Settings");
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new LevelUpPreviewSlash(), new LevelUpTextSlash(), new LevelUpToggleSlash(gs));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);                                 
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        gs.getId();
    }
    
}
