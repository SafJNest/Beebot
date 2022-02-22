package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Permissions extends Command{
    public Permissions(){
        this.name = "permissions";
        this.aliases = new String[]{"perms", "permission"};
        this.help = "Restituisce i permessi di uno user e dice se e' un admin del server.";
        this.category = new Category("Gestione Membri");
        this.arguments = "[permissions] [@user]";
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        String per = "";
        try {
            
            if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0);
            else
                theGuy = event.getGuild().retrieveMemberById(event.getArgs()).complete();
            if (theGuy.isOwner())
                event.reply(theGuy.getAsMention() + " e' l'owner fa come cazzo vuole ora deleta il server se parli");
            else if (theGuy.hasPermission(Permission.ADMINISTRATOR))
                event.reply(theGuy.getAsMention() + " e' un admin può fare tutto quello che vuole.");
            else{
                for(Permission p :  theGuy.getPermissions())
                    per+=p.getName() + "\n";
                event.reply(theGuy.getAsMention() + " non e' un admin\nQuesti sono i suoi permessi: " + per);
            }
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}
