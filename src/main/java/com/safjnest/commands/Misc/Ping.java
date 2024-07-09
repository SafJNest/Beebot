package com.safjnest.commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * The commands shows the ping of the bot.
 * <p>The bot sends a message, once the message is received, the bot sends a message back, and the ping is calculated.</p>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */

public class Ping extends Command {

    public Ping(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.hidden = true;
        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent e) {
        long time = System.currentTimeMillis();
        e.reply("Pong!", response -> {
            response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
        });
    }
   
}