package com.safjnest.Commands.ManageGuild;

import java.util.Collection;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.SlashCommandsHandler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;



public class EnableSlash extends Command {
    private SlashCommandsHandler sch;

    public EnableSlash(SlashCommandsHandler sch){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.sch = sch;
    }

	@Override
	protected void execute(CommandEvent event) {
            Collection<CommandData> commandDataList = sch.getCommandData();
            event.getGuild().updateCommands().addCommands(commandDataList).queue();
            event.reply("Slash command are fired up and ready to serve!");
        }
	}
