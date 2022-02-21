package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.02
 */
public class ServerInfo extends Command{

    public ServerInfo(){
        this.name = "serverinfo";
        this.aliases = new String[]{"servinfo", "serinf"};
        this.help = "Informazioni utili del server in cui ti trovi.";
        this.category = new Category("Gestione Server");
    }

    public static int getGuildUserCount(Guild guild) {
        int i = 0;
        for (Member member : guild.getMembers())
          if (!member.getUser().isBot())
            i++;
        return i;
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Informazioni del server");
        eb.setThumbnail(event.getGuild().getIconUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome del server", "```" + event.getGuild().getName() + "```", true);
        eb.addField("ID dell'Owner", "```" + event.getGuild().getOwnerId() + "```" , true);

        eb.addField("Descrizione del server", "```" + ((event.getGuild().getDescription() == null) ? "Descrizione non trovata" : event.getGuild().getDescription()) + "```", false);

        eb.addField("Tier del Boost", "```" + event.getGuild().getBoostTier().name() + "```", true);
        eb.addField("Numero di Boost", "```" + String.valueOf(event.getGuild().getBoostCount()) + "```", true);
        eb.addField("Ruolo dei Booster", "```" + ((event.getGuild().getBoostRole() == null) ? "NONE" : event.getGuild().getBoostRole().getName()) + "```", true);

        eb.addField("Numero dei Membri del server [" + String.valueOf(event.getGuild().getMemberCount()) + "]", "```" + "Membri: " + event.getGuild().getMembers().stream().filter(member -> !member.getUser().isBot()).count() + " | Bot: " + event.getGuild().getMembers().stream().filter(member -> member.getUser().isBot()).count() + "```", false);

        eb.addField("Livello contenuti espliciti", "```" + event.getGuild().getExplicitContentLevel().name() + "```", true);
        eb.addField("Livello NSFW", "```" + event.getGuild().getNSFWLevel().toString() + "```", true);

        eb.addField("Data di creazione del server", "```" + DateTimeFormatter.ISO_LOCAL_DATE.format(event.getGuild().getTimeCreated()) + "```", false); //TODO cambia formato

        eb.addField("Regione", "```" + event.getGuild().getLocale().toString() + "```", true);
        eb.addField("Livello MFA richiesto", "```" + event.getGuild().getRequiredMFALevel().toString() + "```", true);
        eb.addField("Livello verificazione", "```" + event.getGuild().getVerificationLevel().toString() + "```", true);
        
        event.reply(eb.build());

        //event.reply(event.getGuild().getCategories().toString()); TODO usa sti schifi
        //event.reply(event.getGuild().getChannels().toString());
    }
}
