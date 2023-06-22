package com.safjnest.SlashCommands.Settings.Welcome;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Guild.GuildSettings;

public class WelcomeSlash extends SlashCommand{
    private SQL sql;
    private GuildSettings gs;

    public WelcomeSlash(SQL sql, GuildSettings gs){
        this.name = "welcome";//this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        //this.aliases = new CommandsLoader().getArray(this.name, "alias");
        //this.help = new CommandsLoader().getString(this.name, "help");
        //this.cooldown = new CommandsLoader().getCooldown(this.name);
        //this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        //this.arguments = new CommandsLoader().getString(this.name, "arguments");
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new WelcomeMoveSlash(), new WelcomePreviewSlash(), new WelcomeTextSlash(), new WelcomeToggleSlash());
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);                                 
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
