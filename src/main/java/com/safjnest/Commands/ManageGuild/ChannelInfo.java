package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
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
        this.help = "Il bot restituisce tutte le informazioni relative ad un canale, sia vocale che testuale.";
        this.category = new Category("Gestione Server");
        this.arguments = "[channelinfo] [#canale/id canale]";
    }

	@Override
	protected void execute(CommandEvent event) {
        TextChannel c = null;
        VoiceChannel v = null;
        GuildChannel gc = null;
        if(event.getMessage().getMentionedChannels().size() > 0) 
            gc = event.getMessage().getMentionedChannels().get(0);
        else if(event.getArgs() != null) 
            gc = event.getGuild().getGuildChannelById(event.getArgs());
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("**INFORMAZIONI SUL CANALE**");
        eb.setColor(new Color(116, 139, 151));
        if(gc.getType().isAudio()){
            v = event.getGuild().getVoiceChannelById(event.getArgs());
            eb.addField("Nome Canale", "```" + v.getName() + "```", true);   
            eb.addField("ID Canale", "```" + v.getId() + "```", true);   

            eb.addField("BitRate Canale", "```" + v.getBitrate() + "```", false);   
            eb.addField("Limite di utenti", "```" 
                        + v.getMembers().size() 
                        +"/"
                        + ((v.getUserLimit()==0)
                            ?"∞"
                            :v.getUserLimit()) 
                        + "```", false);
            
            eb.addField("Tipo Canale", "```" + v.getType()+ "```", true);   
            eb.addField("Categoria", "```" + v.getParentCategory().getName() + "```", true);   

            eb.addField("Data creazione Canale", "```" + dtf.format(v.getTimeCreated())+ "```", false);

        }else{
            c = event.getGuild().getTextChannelById(gc.getId());
            eb.addField("Nome Canale", "```" + c.getName() + "```", true);   
            eb.addField("ID Canale", "```" + c.getId() + "```", true);   

            eb.addField("Topic Canale", "```" 
                       + ((c.getTopic()==null)
                            ?"Nessun topic per il canale"
                            :c.getTopic()) 
                       + "```", false); 

            eb.addField("Tipo Canale", "```" + c.getType()+ "```", true);   
            eb.addField("Categoria Canale", "```" + c.getParentCategory().getName() + "```", true);   

            eb.addField("Data creazione Canale", "```" + dtf.format(c.getTimeCreated())+ "```", false);
        } 
        event.reply(eb.build());
	}
}