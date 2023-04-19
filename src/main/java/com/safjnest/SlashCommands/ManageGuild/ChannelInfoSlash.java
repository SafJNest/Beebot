package com.safjnest.SlashCommands.ManageGuild;

import java.awt.Color;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DateHandler;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.CommandsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class ChannelInfoSlash extends SlashCommand {

    public ChannelInfoSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Channel to get the information about", false));
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String id = String.valueOf(event.getOption("channel").getAsChannel().getId());
        GuildChannel gc = event.getGuild().getGuildChannelById(id);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("**CHANNEL INFO**");
        eb.setColor(Color.decode(
            BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
        switch (gc.getType().getId()){

            case 0:
                TextChannel c = event.getGuild().getTextChannelById(gc.getId());
                eb.addField("Channel name", "```" + c.getName() + "```", true);   
                eb.addField("Channel ID", "```" + c.getId() + "```", true);   

                eb.addField("Channel Topic", "```" 
                        + ((c.getTopic()==null)
                                ?"None"
                                :c.getTopic()) 
                        + "```", false); 

                eb.addField("Type", "```" + c.getType() + "```", true);
                eb.addField("Category", "```" + 
                                        ((c.getParentCategory() != null)
                                            ? c.getParentCategory().getName()
                                            : "Its not under a category") 
                                        + "```", true);

                eb.addField("Channel created on", "```" + DateHandler.formatDate(c.getTimeCreated()) + "```", false);
            break;

            case 2:
                VoiceChannel v = event.getGuild().getVoiceChannelById(gc.getId());
                eb.addField("Channel name", "```" + v.getName() + "```", true);   
                eb.addField("Channel ID", "```" + v.getId() + "```", true);   
                eb.addBlankField(true);
                eb.addField("BitRate ", "```" + v.getBitrate()/1000 + "kbps```", true);   
                eb.addField("Number limit", "```" 
                            + v.getMembers().size() 
                            +"/"
                            + ((v.getUserLimit()==0)
                                ?"∞"
                                :v.getUserLimit()) 
                            + "```", true);

                if(v.getMembers().size() != 0){
                    List<String> users = PermissionHandler.getMaxFieldableUserNames(v.getMembers(), 1024);
                    eb.addField("Members ["
                    + v.getMembers().size() + "] "
                    + "(Printed " + users.size() + ")", "```"
                    + (users.size() == 0
                        ? "empty"
                        : users.toString().substring(1, users.toString().length() - 1))
                    + "```", false);
                }else{
                    eb.addField("Members", "```Channel is now empty```", false);
                }
                
                
                eb.addField("Type", "```" + v.getType()+ "```", true);   
                eb.addField("Category", "```" + 
                                        ((v.getParentCategory() != null)
                                            ? v.getParentCategory().getName()
                                            : "Its not under a category") 
                                        + "```", true);  
                eb.addField("Created", "```" + v.getTimeCreated().atZoneSimilarLocal(ZoneId.of("Europe/Rome")) + " | " + DateHandler.formatDate(v.getTimeCreated())+ "```", false);
            break;

            case 4:
                net.dv8tion.jda.api.entities.channel.concrete.Category ct = event.getGuild().getCategoryById(gc.getId());
                eb.addField("Category name", "```" + ct.getName() + "```", true);   
                eb.addField("Category ID", "```" + ct.getId() + "```", true);   

                eb.addField("Contains", "```"+ct.getChannels().size() + " channels"+"```", true);
                eb.addField("Type", "```" + ct.getType() + "```", true);

                eb.addField("Category created on", "```" + DateHandler.formatDate(ct.getTimeCreated()) + "```", false);
            break;

            case 13:
            StageChannel sg = event.getGuild().getStageChannelById(gc.getId());
            eb.addField("Stage name", "```" + sg.getName() + "```", true);   
                eb.addField("Stage ID", "```" + sg.getId() + "```", true);   

                eb.addField("BitRate ", "```" + sg.getBitrate()/1000 + "kbps```", false);   
                eb.addField("Number limit", "```" 
                            + sg.getMembers().size() 
                            +"/"
                            + ((sg.getUserLimit()==0)
                                ?"∞"
                                :sg.getUserLimit()) 
                            + "```", false);


                if(sg.getStageInstance() != null){
                    eb.addField("Is live", "```In live```", true);
                    if(sg.getStageInstance().getSpeakers()!=null){
                        List<String>users = PermissionHandler.getMaxFieldableUserNames(sg.getStageInstance().getSpeakers(), 1024);
                        eb.addField("Members ["
                        + sg.getStageInstance().getSpeakers().size() + "] "
                        + "(Printed " + users.size() + ")", "```"
                        + (users.size() == 0
                            ? "empty"
                            : users.toString().substring(1, users.toString().length() - 1))
                        + "```", false);
                    }
    
                    if(sg.getStageInstance().getTopic()!=null){
                        eb.addField("Topic", "```"+sg.getStageInstance().getTopic()+"```", true);
                    }
                }else{
                    eb.addField("Is live", "```Is not in live```", true);
                }
            
                eb.addField("Type", "```" + sg.getType() + "```", true);
                eb.addField("Category", "```" + 
                                        ((sg.getParentCategory() != null)
                                            ? sg.getParentCategory().getName()
                                            : "Its not under a category") 
                                        + "```", true);

                eb.addField("Channel created on", "```" + DateHandler.formatDate(sg.getTimeCreated()) + "```", false);
            break;

            case 5:
            NewsChannel nw = event.getGuild().getNewsChannelById(gc.getId());
            eb.addField("Channel name", "```" + nw.getName() + "```", true);   
                eb.addField("Channel ID", "```" + nw.getId() + "```", true);   
                eb.addField("Channel Topic", "```" 
                        + ((nw.getTopic()==null)
                                ?"None"
                                :nw.getTopic()) 
                        + "```", false); 

                eb.addField("Type", "```" + nw.getType() + "```", true);
                eb.addField("Category", "```" + 
                                        ((nw.getParentCategory() != null)
                                            ? nw.getParentCategory().getName()
                                            : "Its not under a category") 
                                        + "```", true);

                eb.addField("Channel created on", "```" + DateHandler.formatDate(nw.getTimeCreated()) + "```", false);
            break;

            case 15:
            ForumChannel fr = event.getGuild().getForumChannelById(gc.getId());
            eb.addField("Forum name", "```" + fr.getName() + "```", true);   
                eb.addField("Forum ID", "```" + fr.getId() + "```", true);   
                eb.addField("Channel Topic", "```" 
                        + ((fr.getTopic()==null)
                                ?"None"
                                :fr.getTopic()) 
                        + "```", false); 

                eb.addField("Type", "```" + fr.getType() + "```", true);
                eb.addField("Category", "```" + 
                                        ((fr.getParentCategory() != null)
                                            ? fr.getParentCategory().getName()
                                            : "Its not under a category") 
                                        + "```", true);

                eb.addField("Channel created on", "```" + DateHandler.formatDate(fr.getTimeCreated()) + "```", false);
            break;

        } 
        event.deferReply(false).addEmbeds(eb.build()).queue();
	}
}