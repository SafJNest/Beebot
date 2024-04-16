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

        String args = event.getArgs();

        if(args.startsWith("list")) {
            StringBuilder aliasList = new StringBuilder();
            userData.getAliases().forEach((name, content) -> 
                aliasList.append(name).append(": ").append(content.getCommand()).append("\n"));
            event.reply(aliasList.toString());
            return;
        }

        String[] argss = args.split(" ", 2);

        if(argss.length < 2) {
            event.reply("Invalid syntax, valid syntaxes are: *alias* aliasName command | *alias* list | *alias* delete aliasName");
            return;
        }

        if(args.startsWith("delete")) {
            String toDelete = args.split(" ", 2)[1];
            if(userData.deleteAlias(toDelete))
                event.reply(toDelete + " deleted");
            else
                event.reply("No such alias or error deleting alias " + toDelete);
            return;
        }

        String aliasName = args.split(" ", 2)[0];
        String command = args.split(" ", 2)[1];

        if (userData.getAliases().get(aliasName) != null) {
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