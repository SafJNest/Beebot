package com.safjnest.Commands.Admin;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.App;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.PermissionHandler;

public class Shutdown extends Command{
    /**
     * Default constructor for the class.
     */
    public Shutdown(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.hidden = true;
    }
    /**
     * This method is called every time a member executes the command.
    */
     @Override
    protected void execute(CommandEvent e) {
        String bot = e.getArgs();
        if(bot.equals("")){
            e.reply("Please specify a bot to shutdown.");
            return;
        }
        if(!PermissionHandler.isUntouchable(e.getAuthor().getId()))
            return;
        e.reply("Shutting down " + bot + "...");
        App.shutdown(bot);
    }
}