package com.safjnest.Commands.ManageGuild;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.Utilities.CommandsHandler;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.Guild;



public class DisableSlash extends Command {

    public DisableSlash(){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        //disable all the slash commands
        List<String> commandIds = new ArrayList<>();
        Guild guild = event.getGuild();
        for (net.dv8tion.jda.api.interactions.commands.Command command : guild.retrieveCommands().complete()) {
            commandIds.add(command.getId());
        }
        //delete commands
        for(String commandId : commandIds){
            guild.deleteCommandById(commandId).queue();
        }
        event.reply("Default commands are a poor alternative");
	}
}