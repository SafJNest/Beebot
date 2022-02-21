package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class UserInfo extends Command{

    public UserInfo(){
        this.name = "userinfo";
        this.aliases = new String[]{"usinf"};
        this.help = "Informazioni utili di uno user.";
        this.arguments = "[userinfo] [@user]";
        this.category = new Category("Gestione Server");
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();

        User theGuy;
        if(event.getMessage().getMentionedMembers().size() > 0)
            theGuy = event.getMessage().getMentionedMembers().get(0).getUser();
        else{
            try {
                theGuy = event.getJDA().retrieveUserById(event.getArgs()).complete();
            } catch (Exception e) {
                theGuy = event.getAuthor();
            }
        }
        
        eb.setTitle("Informazioni dello user");
        eb.setThumbnail(theGuy.getAvatarUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome", "```" + theGuy.getAsTag() + "```", true);
        eb.addField("ID", "```" + theGuy.getId() + "```" , true);

        eb.addField("Ruoli (?)", "```" + DateTimeFormatter.ISO_LOCAL_DATE.format(theGuy.getTimeCreated()) + "```", false);//TODO add roles in server

        eb.addField("Nickname", "```" + event.getGuild().getMember(theGuy).getNickname() + "```", true);
        eb.addField("è un bot", "```" + (theGuy.isBot() ? "si" : "no") + "```" , true);

        eb.addField("è un admin", "```" + (event.getGuild().getMember(theGuy).hasPermission(Permission.ADMINISTRATOR) ? "si" : "no") + "```", false);

        eb.addField("quando è entrato nel server", "```" + DateTimeFormatter.ISO_LOCAL_DATE.format(event.getGuild().getMember(theGuy).getTimeJoined()) + "```", false); //TODO change format to 12/12/12 and add time, add time from event

        eb.addField("creazione dell'account", "```" + DateTimeFormatter.ISO_LOCAL_DATE.format(theGuy.getTimeCreated()) + "```", false);

        event.reply(eb.build());
    }
}