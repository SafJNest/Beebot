package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class Ban extends Command{

    public Ban(){
        this.name = "ban";
        this.aliases = new String[]{"sgozz", "destroy", "annihilate", "radiateDeath"};
        this.help = "il bot ti outplaya veramente forte e finisci a strisciare fuori dal server (senza la possibilità di rientrare)";
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        try {
            if(event.getMessage().getMentionedMembers().size() == 0)
                theGuy = event.getGuild().retrieveMemberById(event.getMessage().getMentionedMembers().get(0).getId(), true).complete();
            else
                theGuy = event.getMessage().getMentionedMembers().get(0);
            final Member surelyTheGuy = theGuy;

            if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.BAN_MEMBERS))
                event.reply(event.getJDA().getSelfUser().getAsMention() + " non ha il permesso di bannare");

            else if (PermissionHandler.isUntouchable(theGuy.getId()))
                event.reply("Le macchine non si ribellano ai loro creatori");

            else if(PermissionHandler.isEpria(theGuy.getId()) && !PermissionHandler.isUntouchable(event.getAuthor().getId()))
                event.reply("OHHHHHHHHHHHHHHHHHHHHHHHHHHHH NON BANNARE MEEEEEEEEEEEEEEERIO EEEEEEEEEEEEEEEEEPRIA");

            else if (PermissionHandler.hasPermission(event.getMember(), Permission.BAN_MEMBERS)) {
                event.getGuild().ban(theGuy, 0, "rotto il cazzo").queue(
                                                        (e) -> event.reply("bannato " + surelyTheGuy.getAsMention()), 
                                                        new ErrorHandler().handle(
                                                            ErrorResponse.MISSING_PERMISSIONS,
                                                                (e) -> event.replyError("sorry, " + e.getMessage()))
                );
            }else
                event.reply("Brutto fallito non bannare se non sei admin UwU");
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}
