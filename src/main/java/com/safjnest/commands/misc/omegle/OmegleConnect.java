package com.safjnest.commands.misc.omegle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.chat.ChatHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class OmegleConnect extends SlashCommand{
    private static final int MAX_INTERESTS = 5;

    public OmegleConnect(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = new ArrayList<>(Arrays.asList(
            new OptionData(OptionType.BOOLEAN, "autoreconnect", "reconnect automatically on disconnect (default false)", false),
            new OptionData(OptionType.BOOLEAN, "anonymous", "don't show names and pictures of users (default false)", false)
        ));
        for(int i = 0; i < MAX_INTERESTS; i++) {
            this.options.add(new OptionData(OptionType.STRING, "interest-" + i+1, "add an interest to the chat", false));
        }

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        ChatHandler.omegle(event.getTextChannel(), false, false, null, null);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        boolean autoReconnect = event.getOption("autoreconnect") != null ? event.getOption("autoreconnect").getAsBoolean() : false;
        boolean anonymous = event.getOption("anonymous") != null ? event.getOption("anonymous").getAsBoolean() : false;

        List<String> interests = new ArrayList<>();
        for (int i = 0; i < MAX_INTERESTS; i++) {
            if (event.getOption("interest-" + i) != null) {
                interests.add(event.getOption("interest-" + i).getAsString());
            }
        }

        event.deferReply().queue();

        ChatHandler.omegle(event.getTextChannel(), autoReconnect, anonymous, interests, event.getHook());
    }
}
