package com.safjnest.commands.misc.omegle;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.chat.ChatHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class OmegleConnect extends SlashCommand{

    public OmegleConnect(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.BOOLEAN, "autoreconnect", "reconnect automatically on disconnect (default false)", false),
            new OptionData(OptionType.BOOLEAN, "anonymous", "don't show names and pictures of users (default false)", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        ChatHandler.omegle(event.getTextChannel(), false, false, null);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        boolean autoReconnect = event.getOption("autoreconnect") != null ? event.getOption("autoreconnect").getAsBoolean() : false;
        boolean anonymous = event.getOption("anonymous") != null ? event.getOption("anonymous").getAsBoolean() : false;

        event.deferReply().queue();
        
        ChatHandler.omegle(event.getTextChannel(), autoReconnect, anonymous, event.getHook());
    }
}
