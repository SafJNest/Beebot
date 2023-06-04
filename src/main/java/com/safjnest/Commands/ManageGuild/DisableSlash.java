package com.safjnest.Commands.ManageGuild;

import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.Commands.CommandsLoader;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class DisableSlash extends Command {

    public DisableSlash() {
        this.name = this.getClass().getSimpleName();
        ;
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        event.getGuild().updateCommands().queue();
        event.reply("Default commands are a poor alternative");

        String query = "INSERT INTO guild_settings (guild_id, bot_id, has_slash) VALUES (" + event.getGuild().getId()
                + ", " + event.getJDA().getSelfUser().getId() + ", true) ON DUPLICATE KEY UPDATE has_slash = false";
        DatabaseHandler.getSql().runQuery(query);
    }
}