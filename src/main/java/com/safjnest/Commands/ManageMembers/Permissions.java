package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Permissions extends Command{
    public Permissions(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        String per = "";
        try {
            
            if(event.getMessage().getMentions().getMembers().size() > 0)
                theGuy = event.getMessage().getMentions().getMembers().get(0);
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
