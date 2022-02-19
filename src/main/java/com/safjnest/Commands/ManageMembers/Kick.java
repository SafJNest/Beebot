package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Kick extends Command{

    public Kick(){
        this.name = "kick";
        this.aliases = new String[]{"lightSgozz", "espulsione"};
        this.category = new Category("MemberManage");
        this.arguments = "[kick] [@user]";
        this.help = "Il bot ti outplaya leggermente e finisci a strisciare fuori dal server (con la possibilità di rientrare).";
    }

    @Override
    protected void execute(CommandEvent event) {
        User theGuy = null;
        try {
            if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0).getUser();
            else
                theGuy = event.getJDA().retrieveUserById(event.getArgs()).complete();
            final User surelyTheGuy = theGuy;

            if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.KICK_MEMBERS))
                event.reply(event.getJDA().getSelfUser().getAsMention() + " non ha il permesso di kickare");

            else if (PermissionHandler.isUntouchable(theGuy.getId()))
                event.reply("Le macchine non si ribellano ai loro creatori");

            else if(PermissionHandler.isEpria(theGuy.getId()) && !PermissionHandler.isUntouchable(event.getAuthor().getId()))
                event.reply("OHHHHHHHHHHHHHHHHHHHHHHHHHHHH NON KIKKARE MEEEEEEEEEEEEEEERIO EEEEEEEEEEEEEEEEEPRIA");

            else if (PermissionHandler.hasPermission(event.getMember(), Permission.KICK_MEMBERS)) {
                event.getGuild().kick(surelyTheGuy.getId()).queue(
                                                (e) -> event.reply("kickkato " + surelyTheGuy.getAsMention()), 
                                                new ErrorHandler().handle(
                                                    ErrorResponse.MISSING_PERMISSIONS,
                                                        (e) -> event.replyError("sorry, " + e.getMessage()))
                );
            }else
                event.reply("Brutto fallito non kickare se non sei admin UwU");
        } catch (Exception e) {
            event.replyError("sorry, catched " + e.getMessage());
        }
    }
}
