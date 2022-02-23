package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.safjnest.Utilities.PermissionHandler;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.internal.entities.CategoryImpl;
import net.dv8tion.jda.api.entities.Category;
/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class ChannelInfo extends Command {
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy' 'HH:mm");

    public ChannelInfo(){
        this.name = "channelinfo";
        this.aliases = new String[]{"infochannel", "ci","ic","channel"};
        this.help = "Il bot cancella i messaggi in un canale di testo fino ad un massimo di 99 messaggi(100 incluso il comando).";
        this.category = new Category("Gestione Server");
        this.arguments = "[clear] [n messaggi]";
    }

	@Override
	protected void execute(CommandEvent event) {
        TextChannel c = null;
        VoiceChannel v = null;
        GuildChannel gc = null;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("**INFORMAZIONI SUL CANALE**");
        eb.setColor(new Color(116, 139, 151));
        if(gc.getType().isAudio() && (event.getArgs() != null)){
            v = event.getGuild().getVoiceChannelById(event.getArgs());
            eb.addField("Nome Canale", "```" + v.getName() + "```", true);   
            eb.addField("ID Canale", "```" + v.getId() + "```", true);   

            eb.addField("BitRate Canale", "```" + v.getBitrate() + "```", false);   

            eb.addField("Tipo Canale", "```" + v.getType()+ "```", true);   
            eb.addField("Categoria", "```" + v.getParentCategory().getName() + "```", true);   

            eb.addField("Data creazione Canale", "```" + dtf.format(v.getTimeCreated())+ "```", false);

        }else{
            c = (event.getArgs()!=null) ? event.getGuild().getTextChannelById(event.getArgs()) : event.getTextChannel();
            eb.addField("Nome Canale", "```" + c.getName() + "```", true);   
            eb.addField("ID Canale", "```" + c.getId() + "```", true);   

            eb.addField("Topic Canale", "```" + c.getTopic() + "```", false);   

            eb.addField("Tipo Canale", "```" + c.getType()+ "```", true);   
            eb.addField("Tipo Canale", "```" + c.getParentCategory().getName() + "```", true);   

            eb.addField("Data creazione Canale", "```" + dtf.format(c.getTimeCreated())+ "```", false);
        } 
        event.reply(eb.build());
	}
}