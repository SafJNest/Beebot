package com.safjnest.commands.misc;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SpecialChar extends SlashCommand{

    public SpecialChar(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "command", "Name of the bugged command", true)
                .addChoice("`", "grave_accent")
                .addChoice("~", "tilde")
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String command = event.getOption("command").getAsString();
        switch(command){
            case "grave_accent":
                event.deferReply(false).addContent("`").queue();
                break;
            case "tilde":
                event.deferReply(false).addContent("~").queue();
                break;
        }
    }
}
