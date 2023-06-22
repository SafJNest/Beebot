package com.safjnest.SlashCommands.Settings.Leave;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Guild.GuildSettings;

public class LeaveSlash extends SlashCommand {

    private SQL sql;
    private GuildSettings gs;

    public LeaveSlash(SQL sql, GuildSettings gs){
        this.name = "leave";//this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        //this.aliases = new CommandsLoader().getArray(this.name, "alias");
        //this.help = new CommandsLoader().getString(this.name, "help");
        //this.cooldown = new CommandsLoader().getCooldown(this.name);
        //this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        //this.arguments = new CommandsLoader().getString(this.name, "arguments");
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new LeaveMoveSlash(), new LeavePreviewSlash(), new LeaveTextSlash(), new LeaveToggleSlash());
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);                                 
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
    
}
