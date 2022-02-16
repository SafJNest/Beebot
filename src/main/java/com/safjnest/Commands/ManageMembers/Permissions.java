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
        this.aliases = new String[]{"perms"};
        this.help = "Restituisce i permessi di uno user e dice se e' un admin del server| [permissions] [@user]";
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        try {
            if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0);
            else
                theGuy = event.getGuild().retrieveMemberById(event.getArgs()).complete();

            if (theGuy.isOwner())
                event.reply(theGuy.getAsMention() + " e' l'owner\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString());
            else if (theGuy.hasPermission(Permission.ADMINISTRATOR))
                event.reply(theGuy.getAsMention() + " e' un admin\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString());
            else
                event.reply(theGuy.getAsMention() + " non e' un admin\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString());
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}
