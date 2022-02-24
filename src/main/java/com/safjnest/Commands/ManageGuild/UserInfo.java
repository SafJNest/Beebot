package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    private List<String> getMaxFieldableRoleNames(CommandEvent event, User theGuy) {
        List<String> finalRoles= new ArrayList<String>();
        int rolesLenght = 0;
        for (int i = 0; i < event.getGuild().getMember(theGuy).getRoles().size(); i++) {
            rolesLenght += event.getGuild().getMember(theGuy).getRoles().get(i).getName().length() + 2;
            if(rolesLenght >= 1024)
                break;
            finalRoles.add(event.getGuild().getMember(theGuy).getRoles().get(i).getName());
        }
        return finalRoles;
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
        
        eb.setTitle(":busts_in_silhouette:Informazioni sullo user:busts_in_silhouette:");
        eb.setThumbnail(theGuy.getAvatarUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome", "```" + theGuy.getAsTag() + "```", true);
        eb.addField("ID", "```" + theGuy.getId() + "```" , true);

        List<String> RoleNames = getMaxFieldableRoleNames(event, theGuy);
        eb.addField("Ruoli ["
                    + event.getGuild().getMember(theGuy).getRoles().size() + "] "
                    + "(stampati " + RoleNames.size() + ")", "```"
                    + (RoleNames.size() == 0
                        ? "NO ROLES"
                        : RoleNames.toString().substring(1, RoleNames.toString().length() - 1))
                    + "```", false);

        eb.addField("Nickname", "```"
                    + (event.getGuild().getMember(theGuy).getNickname() == null
                        ? "NO NICKNAME"
                        : event.getGuild().getMember(theGuy).getNickname())
                    + "```", true);

        eb.addField("è un bot", "```"
                    + ((theGuy.isBot() || PermissionHandler.isEpria(theGuy.getId()))
                        ? "si"
                        : "no")
                    + "```" , true);

        String permissionNames = "";
        for(String permission : PermissionHandler.getPermissionNames(event.getGuild().getMember(theGuy))){
            permissionNames += permission + "\n";
        }
        eb.addField("Permessi del server", "```"
                    + (event.getGuild().getMember(theGuy).hasPermission(Permission.ADMINISTRATOR)
                        ? "👑 Amministratore (tutti i permessi)"
                        : permissionNames.substring(1, permissionNames.length() - 1))
                    + "```", false);

        eb.addField("Entrato nel server il (dd/mm/yyyy)", "```" 
                    + dtf.format(event.getGuild().getMember(theGuy).getTimeJoined()) 
                    + " (" + event.getGuild().getMember(theGuy).getTimeJoined().until(OffsetDateTime.now(), ChronoUnit.DAYS) + " giorni fa)"
                    + "```", false);

        eb.addField("Creato l'account il (dd/mm/yyyy)", "```"
                    + dtf.format(theGuy.getTimeCreated()) 
                    + " (" + theGuy.getTimeCreated().until(OffsetDateTime.now(), ChronoUnit.DAYS) + " giorni fa)"
                    + "```", false);
        
        event.reply(eb.build());
    }
}