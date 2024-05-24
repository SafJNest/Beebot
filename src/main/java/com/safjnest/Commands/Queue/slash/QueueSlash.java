package com.safjnest.commands.Queue.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.types.EmbedType;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.CommandsLoader;


public class QueueSlash extends SlashCommand{
    
    public QueueSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        QueueHandler.sendEmbed(event, EmbedType.QUEUE, ReplyType.REPLY);
    }
}