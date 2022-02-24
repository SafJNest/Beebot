package com.safjnest.Commands.ManageGuild;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.lang.model.util.Elements;

import com.safjnest.Utilities.PermissionHandler;

import org.jdom2.Document;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class EmojiInfo extends Command {
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy' 'HH:mm");

    public EmojiInfo(){
        this.name = "emoji";
        this.aliases = new String[]{"em"};
        this.help = "Il bot cancella i messaggi in un canale di testo fino ad un massimo di 99 messaggi(100 incluso il comando).";
        this.category = new Category("Gestione Server");
        this.arguments = "[clear] [n messaggi]";
    }

	@Override
	protected void execute(CommandEvent event) {
        Emote em = (event.getMessage().getEmotes().size()!=0) ? event.getMessage().getEmotes().get(0) : event.getGuild().getEmotesByName(event.getArgs(), true).get(0);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(":laughing: "+"**EMOJI INFO**"+" :laughing:");
        eb.setThumbnail(em.getImageUrl());
        eb.addField("**Nome**", "```" + em.getName() + "```", true);   
        eb.addField("**ID emoji**", "```" + em.getId() + "```", true); 
        eb.addField("**GIF?**",
        (em.isAnimated())
        ?"```✅ Si```"
        :"```❌ No```"
        , true);
        eb.addField("**Emoji URL**", em.getImageUrl(), false);   
        eb.addField("Creata il il (dd/mm/yyyy)", "```" + dtf.format(em.getTimeCreated())+ "```", false);
        event.reply(eb.build());

	}
}