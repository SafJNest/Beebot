package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class Mute extends Command{

    public Mute(){
        this.name = "Mute";
        this.aliases = new String[]{"dontlistentothesound"};
        this.help = "il bot ti outplaya leggermente e finisci a strisciare fuori dal server (con la possibilità di rientrare)";
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        try {
            if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0);
            else
                theGuy = event.getGuild().retrieveMemberById(event.getArgs()).complete();
            final Member surelyTheGuy = theGuy;

            if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.VOICE_MUTE_OTHERS))
                event.reply(event.getJDA().getSelfUser().getAsMention() + " non ha il permesso di kickare");

            else if (PermissionHandler.isUntouchable(theGuy.getId()))
                event.reply("Le macchine non si ribellano ai loro creatori");

            else if(PermissionHandler.isEpria(theGuy.getId()) && !PermissionHandler.isUntouchable(event.getAuthor().getId()))
                event.reply("OHHHHHHHHHHHHHHHHHHHHHHHHHHHH NON MUTARE MEEEEEEEEEEEEEEERIO EEEEEEEEEEEEEEEEEPRIA, solo i king possono.");

            else if (PermissionHandler.hasPermission(event.getMember(), Permission.VOICE_MUTE_OTHERS)) {
                event.getGuild().mute(surelyTheGuy, true).queue(
                                                        (e) -> event.reply("mutato " + surelyTheGuy.getAsMention()), 
                                                        new ErrorHandler().handle(
                                                            ErrorResponse.MISSING_PERMISSIONS,
                                                                (e) -> event.replyError("sorry, " + e.getMessage()))
                );
            } else
                event.reply("Brutto fallito non kickare se non sei admin UwU");
        } catch (Exception e) {
            event.replyError("frake, " + e.getMessage());
        }
    }
}
