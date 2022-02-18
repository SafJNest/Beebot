package com.safjnest.Commands.ManageGuild;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.02*/
public class ServerInfo extends Command{

    public ServerInfo(){
        this.name = "serverinfo";
        this.aliases = new String[]{"servinf"};
        this.help = "Informazioni utili del server in cui ti trovi";
    }

    @Override
    protected void execute(CommandEvent event) {

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(event.getGuild().getName());
        eb.setThumbnail(event.getGuild().getIconUrl());
        eb.setColor(new Color(116, 139, 151));
        eb.addField("Descrizione", "`" + ((event.getGuild().getDescription() == null) ? "non c'è frocio" : event.getGuild().getDescription()) + "`", true);
        eb.addField("Owner", "`" + event.getGuild().getOwnerId() + "`" , true); 
        eb.addField("BoostCounter", "`" + String.valueOf(event.getGuild().getBoostCount()) + "`", true);
        eb.addField("Ruolo Booster", "`" + ((event.getGuild().getBoostRole() == null) ? "`non c'è frocio`" : event.getGuild().getBoostRole().getName()) + "`", true);
        eb.addField("Tier del Boost", "`" + event.getGuild().getBoostTier().name() + "`", true);
        eb.addField("explicit content level", "`" + event.getGuild().getExplicitContentLevel().name() + "`", true);
        eb.addField("locale", "`" + event.getGuild().getLocale().toString() + "`", true);      
        eb.addField("member count", "`" + String.valueOf(event.getGuild().getMemberCount()) + "`", true);
        eb.addField("time created", "`" + event.getGuild().getTimeCreated().toString() + "`", true);
        eb.addField("NSFW level", "`" + event.getGuild().getNSFWLevel().toString() + "`", true);
        eb.addField("required MFA level", "`" + event.getGuild().getRequiredMFALevel().toString() + "`", true);
        eb.addField("verification level", "`" + event.getGuild().getVerificationLevel().toString() + "`", true);
        event.reply(eb.build());

        //event.reply("splash " + event.getGuild().getSplashUrl());
        //event.reply("vanity " + event.getGuild().getVanityUrl());
        //event.reply(event.getGuild().getCategories().toString());
        //event.reply(event.getGuild().getChannels().toString());
    }
}
