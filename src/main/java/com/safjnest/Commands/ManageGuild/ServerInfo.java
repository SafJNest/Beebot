package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.02
 */
public class ServerInfo extends Command{

    public ServerInfo(){
        this.name = "serverinfo";
        this.aliases = new String[]{"servinfo", "serinf"};
        this.help = "Informazioni utili del server in cui ti trovi";
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        
        eb.setTitle("Informazioni del server");
        eb.setThumbnail(event.getGuild().getIconUrl());
        eb.setColor(new Color(116, 139, 151));
        
        eb.addField("Nome del server", "```" + event.getGuild().getName() + "```", true);
        eb.addField("ID dell'Owner", "```" + event.getGuild().getOwnerId() + "```" , true);

        eb.addField("Descrizione del server", "```" + ((event.getGuild().getDescription() == null) ? "non c'è frocio" : event.getGuild().getDescription()) + "```", false);

        eb.addField("Numero di Boost", "```" + String.valueOf(event.getGuild().getBoostCount()) + "```", true);
        eb.addField("Ruolo dei Booster", "```" + ((event.getGuild().getBoostRole() == null) ? "non c'è frocio" : event.getGuild().getBoostRole().getName()) + "```", true);
        eb.addField("Tier del Boost", "```" + event.getGuild().getBoostTier().name() + "```", true);

        eb.addField("Data di creazione del server", "```" + DateTimeFormatter.ISO_LOCAL_DATE.format(event.getGuild().getTimeCreated()) + "```", false);

        eb.addField("Livello contenuti espliciti", "```" + event.getGuild().getExplicitContentLevel().name() + "```", true);
        eb.addField("Livello NSFW", "```" + event.getGuild().getNSFWLevel().toString() + "```", true);
        eb.addField("Regione", "```" + event.getGuild().getLocale().toString() + "```", true);

        eb.addField("Numero dei Membri del server", "```" + String.valueOf(event.getGuild().getMemberCount()) + "```", true);
        eb.addField("Livello MFA richiesto", "```" + event.getGuild().getRequiredMFALevel().toString() + "```", true);
        eb.addField("Livello di verificazione", "```" + event.getGuild().getVerificationLevel().toString() + "```", true);
        
        event.reply(eb.build());

        //event.reply(event.getGuild().getCategories().toString());
        //event.reply(event.getGuild().getChannels().toString());
    }
}
