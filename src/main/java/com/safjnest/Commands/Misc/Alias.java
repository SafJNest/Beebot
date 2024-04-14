package com.safjnest.Commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import net.dv8tion.jda.api.entities.User;

import com.safjnest.Utilities.UserData;

/**
 * Mega alias command
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 8.0.2
 */
public class Alias extends Command {

    public Alias() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        User author = event.getAuthor();
        UserData userData = Bot.getUserData(author.getId());

        if(event.getArgs().equals("list")) {
            StringBuilder aliasList = new StringBuilder();
            userData.getAliases().forEach((name, content) -> 
                aliasList.append(name).append(": ").append(content.getCommand()).append("\n"));
            event.reply(aliasList.toString());
            return;
        }

        String args = event.getArgs();
        String aliasName = args.substring(0, args.indexOf(" "));
        String command = args.substring(args.indexOf(" ") + 1);
        
        

        if (userData.getAlias(aliasName) != null) {
            event.reply("Alias already exists.");
            return;
        }

        if (!userData.addAlias(aliasName, command)) {
            event.reply("Failed to create alias.");
            return;
        }

        event.reply("Alias created successfully.");
	}
}


