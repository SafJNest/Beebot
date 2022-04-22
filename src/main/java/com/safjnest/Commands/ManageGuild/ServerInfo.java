package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.DateHandler;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.02
 */
public class ServerInfo extends Command{

    public ServerInfo(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Informazioni del server");
        eb.setThumbnail(guild.getIconUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome del server", "```" 
                    + guild.getName() 
                    + "```", true);
        eb.addField("ID dell'Owner", "```" 
                    + guild.getOwnerId() 
                    + "```" , true);

        eb.addField("Descrizione del server", "```" 
                    + ((guild.getDescription() == null) 
                        ? "Descrizione non trovata" 
                        : guild.getDescription()) 
                    + "```", false);

        eb.addField("ID del Server", "```" 
                    + guild.getId()
                    + "```" , true);
        eb.addField("Regione", "```" 
                    + guild.getLocale().toString() 
                    + "```", true);

        eb.addField("Numero dei Membri del server [" 
                    + String.valueOf(guild.getMemberCount()) + "]", "```"
                    + "Membri: " + guild.getMembers().stream()
                                    .filter(member -> !member.getUser().isBot()).count()
                    + " | Bot: " + guild.getMembers().stream()
                                    .filter(member -> member.getUser().isBot()).count()
                    + "```", false);

        eb.addField("Tier del Boost", "```" 
                    + guild.getBoostTier().name() 
                    + "```", true);
        eb.addField("Numero di Boost", "```" 
                    + String.valueOf(guild.getBoostCount()) 
                    + "```", true);
        eb.addField("Ruolo dei Booster", "```" 
                    + (guild.getBoostRole() == null
                        ? "NONE"
                        : guild.getBoostRole().getName())
                    + "```", true);

        eb.addField("Categorie e canali [" + guild.getChannels().size() + "]", "```" 
                    +    "Categorie: " + guild.getCategories().size() 
                    + " | Testuali: " + guild.getTextChannels().size() 
                    + " | Vocali: " + guild.getVoiceChannels().size() 
                    + "```", false);

        eb.addField("Emoji del server [" + guild.getEmotes().size() + "]", "```" 
                    +    "Normali: " + guild.getEmotes().stream()
                                        .filter(emote -> !emote.isAnimated()).count()
                    + " | Animate: " + guild.getEmotes().stream()
                                        .filter(emote -> emote.isAnimated()).count()
                    + "```", false);

        eb.addField("Livello contenuti espliciti", "```" 
                    + guild.getExplicitContentLevel().name() 
                    + "```", true);
        eb.addField("Livello NSFW", "```" 
                    + guild.getNSFWLevel().toString() 
                    + "```", true);

        List<String> RoleNames = PermissionHandler.getMaxFieldableRoleNames(guild.getRoles(), 195);
        eb.addField("Ruoli del server [" 
                    + guild.getRoles().size() + "] (stampati " 
                    + RoleNames.size() + ")" , "```" 
                    + RoleNames.toString().substring(1, RoleNames.toString().length() - 1) 
                    + "```", false);

        eb.addField("Livello MFA richiesto", "```" 
                    + guild.getRequiredMFALevel().toString() 
                    + "```", true);
        

        eb.addField("Data di creazione del server", "```" 
                    + DateHandler.formatDate(guild.getTimeCreated()) 
                    + "```", false);
        
        event.reply(eb.build());

        //event.reply(guild.getCategories().toString())
        //event.reply(guild.getChannels().toString());
    }
}