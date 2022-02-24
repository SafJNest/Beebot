package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.DateHandler;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class UserInfo extends Command{
    
    public UserInfo() {
        this.name = "userinfo";
        this.aliases = new String[]{"usinf"};
        this.help = "Informazioni utili di uno user.";
        this.arguments = "[userinfo] [@user]";
        this.category = new Category("Gestione Server");
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        User user;
        Member member;

        EmbedBuilder eb = new EmbedBuilder();
        
        if(event.getMessage().getMentionedMembers().size() > 0)
            user = event.getMessage().getMentionedMembers().get(0).getUser();
        else{
            try {
                user = event.getJDA().retrieveUserById(event.getArgs()).complete();
            } catch (Exception e) {
                user = event.getAuthor();
            }
        }
        if(!guild.isMember(user)){
            event.reply("Lo user non fa parte della gilda");
            return;
        }
        member = guild.getMember(user);
        
        eb.setTitle(":busts_in_silhouette:Informazioni sullo user:busts_in_silhouette:");
        eb.setThumbnail(user.getAvatarUrl());
        eb.setColor(new Color(116, 139, 151));

        eb.addField("Nome", "```" + user.getAsTag() + "```", true);
        eb.addField("ID", "```" + user.getId() + "```" , true);

        List<String> RoleNames = PermissionHandler.getMaxFieldableRoleNames(member.getRoles());
        eb.addField("Ruoli ["
                    + member.getRoles().size() + "] "
                    + "(stampati " + RoleNames.size() + ")", "```"
                    + (RoleNames.size() == 0
                        ? "NO ROLES"
                        : RoleNames.toString().substring(1, RoleNames.toString().length() - 1))
                    + "```", false);

        eb.addField("Nickname", "```"
                    + (member.getNickname() == null
                        ? "NO NICKNAME"
                        : member.getNickname())
                    + "```", true);

        eb.addField("è un bot", "```"
                    + ((user.isBot() || PermissionHandler.isEpria(user.getId()))
                        ? "si"
                        : "no")
                    + "```" , true);

        String permissionNames = "";
        for(String permission : PermissionHandler.getFilteredPermissionNames(member))
            permissionNames += "✅ " + permission + "\n";
        eb.addField("Permessi del server", "```"
                    + (member.hasPermission(Permission.ADMINISTRATOR)
                        ? "👑 Amministratore (tutti i permessi)"
                        : permissionNames)
                    + "```", false);

        eb.addField("Entrato nel server il (dd/mm/yyyy)", "```" 
                    + DateHandler.formatDate(member.getTimeJoined())
                    + "```", false);

        eb.addField("Creato l'account il (dd/mm/yyyy)", "```"
                    + DateHandler.formatDate(user.getTimeCreated())
                    + "```", false);
        
        event.reply(eb.build());
        DateHandler.formatDate(user.getTimeCreated());

    }
}