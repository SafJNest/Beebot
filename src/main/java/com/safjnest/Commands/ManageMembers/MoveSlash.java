package com.safjnest.Commands.ManageMembers;


import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class MoveSlash extends SlashCommand {
    
    public MoveSlash() {
        this.name = "move";
        this.options = Arrays.asList(
                new OptionData(OptionType.STRING, "fwe", "gwr")
                    .addChoice("fart ex", "fart ex"), 
                new OptionData(OptionType.CHANNEL, "to", "persone da spostare"));
        this.help = "Consente di spostare le persone e mandarle a fare in curghbuhrugw";
    }
    
    @Override
    public void execute(SlashCommandEvent event) {
        
        long time = System.currentTimeMillis();
        event.deferReply().queue(
            hook -> hook.editOriginalFormat("Pong: %d ms ", System.currentTimeMillis() - time).queue()
        );
    }
}
