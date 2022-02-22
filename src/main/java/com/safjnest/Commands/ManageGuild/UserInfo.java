package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class UserInfo extends Command{
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy' 'HH:mm");

    public UserInfo() {
        this.name = "userinfo";
        this.aliases = new String[]{"usinf"};
        this.help = "Informazioni utili di uno user.";
        this.arguments = "[userinfo] [@user]";
        this.category = new Category("Gestione Server");
    }

    private String getRoles(CommandEvent event, User theGuy) {
        String roles = "";
        for (int i = 0; i < event.getGuild().getMember(theGuy).getRoles().size(); i++) {
            String foo = event.getGuild().getMember(theGuy).getRoles().get(i).getName() + ", ";
            if(roles.length() + foo.length() >= 1024)
                break;
            roles += foo;
        }
        if (roles.length() >= 2)
            roles = roles.substring(0, roles.length() - 2);
        if(roles == null || roles == "")
            roles = "NO ROLES";
        return roles;
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
        if(!event.getGuild().isMember(theGuy)){
            event.reply("Lo user non fa parte della gilda");
            return;
        }
        
        eb.setTitle("Informazioni dello user");
        eb.setThumbnail(theGuy.getAvatarUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome", "```" + theGuy.getAsTag() + "```", true);
        eb.addField("ID", "```" + theGuy.getId() + "```" , true);

        String roles = getRoles(event, theGuy);
        int rolesCount = roles.length() - roles.replace(",", "").length(); //TOFIX magari non prendere il numero dei ruoli cosi`
        eb.addField("Ruoli [" 
                    + event.getGuild().getMember(theGuy).getRoles().size() + "] " 
                    + "(stampati " + (rolesCount == 0 ? rolesCount + 1 : rolesCount) + ")",
                    "```" + roles + "```", false);

        eb.addField("Nickname", "```" + (event.getGuild().getMember(theGuy).getNickname() == null ? "NO NICKNAME" : event.getGuild().getMember(theGuy).getNickname()) + "```", true);
        eb.addField("è un bot", "```" + ((theGuy.isBot() || PermissionHandler.isEpria(theGuy.getId())) ? "si" : "no") + "```" , true);

        eb.addField("è un admin", "```" + (event.getGuild().getMember(theGuy).hasPermission(Permission.ADMINISTRATOR) ? "si" : "no") + "```", false);//TODO aggiungi permessi

        eb.addField("quando è entrato nel server", "```" 
                    + dtf.format(event.getGuild().getMember(theGuy).getTimeJoined()) 
                    + " (" + event.getGuild().getMember(theGuy).getTimeJoined().until(OffsetDateTime.now(), ChronoUnit.DAYS) + " giorni fa)" 
                    + "```", false);

        eb.addField("creazione dell'account", "```" + dtf.format(theGuy.getTimeCreated()) + " (" + theGuy.getTimeCreated().until(OffsetDateTime.now(), ChronoUnit.DAYS) + " giorni fa)" + "```", false);

        event.reply(eb.build());
    }
}