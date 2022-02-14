package com.safjnest.Commands.Misc;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

public class Help extends Command {

    public Help() {
        this.name = "help";
        this.aliases = new String[]{"command", "commands", "info"};
        this.help = "Restituisce il ping del bot. (ms)";
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Help", null);
        eb.setColor(new Color(255, 196, 0));
        eb.setDescription("**Lista dei Comandi del tier 1 bot!**");
        
        for (Command e : event.getClient().getCommands()) {
            eb.addField(e.getName(), e.getHelp(), true);
        }

        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",
                event.getJDA().getSelfUser().getAvatarUrl());

        eb.setFooter("*I numeri primi generati sono crittograficamente sicuri, se vi può essere utile :L", null);
        event.getChannel().sendMessageEmbeds(eb.build())
                .queue();

    }

}
