package com.safjnest.SlashCommands.Settings.Leave;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

public class LeaveSlash extends SlashCommand {


    public LeaveSlash(){
        this.name = "leave";//this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        //this.aliases = new CommandsLoader().getArray(this.name, "alias");
        //this.help = new CommandsLoader().getString(this.name, "help");
        //this.cooldown = new CommandsLoader().getCooldown(this.name);
        //this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        //this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.category = new Category("Settings");
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new LeaveMoveSlash(), new LeavePreviewSlash(), new LeaveTextSlash(), new LeaveDeleteSlash());
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);

    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
    
}
