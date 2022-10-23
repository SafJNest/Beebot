package com.safjnest.Commands.Misc;

import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PingCommand extends SlashCommand {
    
    public PingCommand() {
        this.name = "ping";
        this.options = Collections.singletonList(new OptionData(OptionType.USER, "text", "The text to say.").setRequired(true));
        this.help = "Performs a ping to see the bot's delay";
    }
    
    @Override
    public void execute(SlashCommandEvent event) {
        
        long time = System.currentTimeMillis();
        event.deferReply().queue(
            hook -> hook.editOriginalFormat("Pong: %d ms " + event.getOption("text").getAsUser().getName(), System.currentTimeMillis() - time).queue()
        );
    }
}