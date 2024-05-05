package com.safjnest.SlashCommands.Queue;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.EmbedType;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.ReplyType;


public class PlayerSlash extends SlashCommand{
    
    public PlayerSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        QueueHandler.sendEmbed(event, EmbedType.PLAYER, ReplyType.REPLY);
    }
}