package com.safjnest.commands.queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.types.EmbedType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class Queue extends Command{
    
    public Queue() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        QueueHandler.sendEmbed(event, EmbedType.QUEUE);
    }
}