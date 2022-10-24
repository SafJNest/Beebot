package com.safjnest.SlashCommands.ManageMembers;


import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsHandler;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class MoveSlash extends SlashCommand {
    
    public MoveSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(
                new OptionData(OptionType.STRING, "fwe", "gwr")
                    .addChoice("fart ex", "fart ex"), 
                new OptionData(OptionType.CHANNEL, "to", "persone da spostare"));
    }
    
    @Override
    public void execute(SlashCommandEvent event) {
        
        long time = System.currentTimeMillis();
        event.deferReply().queue(
            hook -> hook.editOriginalFormat("Pong: %d ms ", System.currentTimeMillis() - time).queue()
        );
    }
}
