package com.safjnest.commands.misc.omegle;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import com.safjnest.core.chat.ChatHandler;

public class OmegleDisconnect extends SlashCommand{

    public OmegleDisconnect(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        ChatHandler.omegleDisconnect(event.getTextChannel().getId(), null);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        ChatHandler.omegleDisconnect(event.getTextChannel().getId(), event.getHook());
    }
}
