package com.safjnest.Commands.Misc;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.02
 */
public class Aliases extends Command {

    public Aliases() {
        this.name = "Aliases";
        this.aliases = new String[]{"alias"};
        this.help = "Restituisce tutti gli alias dei vari comandi";
        this.category = new Category("Misc");
        this.arguments = "null";
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Help", null);
        eb.setColor(new Color(0, 255, 213));
        eb.setDescription("**Lista dei Comandi del tier 1 bot!**");
        
        for (Command e : event.getClient().getCommands()) {
            String aliases = "";
            for(String alias : e.getAliases())
                aliases += "- " + alias;
            eb.addField(e.getName(), aliases, true);
        }

        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",
                event.getJDA().getSelfUser().getAvatarUrl());

        eb.setFooter("Per ulteriori inforamzioni su un comando, fare **" + event.getClient().getPrefix()+"help<nome comando>**", null);
        event.getChannel().sendMessageEmbeds(eb.build())
                .queue();

    }

}
