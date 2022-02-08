package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class UnMute extends Command{

    public UnMute(){
        this.name = "unmute";
        this.aliases = new String[]{"listentothesound"};
        this.help = "il bot ti outplaya leggermente e finisci a strisciare fuori dal server (con la possibilità di rientrare)";
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy = null;
        try {
            theGuy = event.getMessage().getMentionedMembers().get(0);
            final Member surelyTheGuy = theGuy;

            if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.VOICE_MUTE_OTHERS))
                event.reply(event.getJDA().getSelfUser().getAsMention() + " non ha il permesso di smutare");
            else if(PermissionHandler.hasPermission(event.getMember(), Permission.VOICE_MUTE_OTHERS) && !theGuy.getVoiceState().isMuted()) {
                event.reply("Non smutare chi non e' smutato");
                return;
            }
            else if (PermissionHandler.hasPermission(event.getMember(), Permission.VOICE_MUTE_OTHERS)) {
                event.getGuild().mute(theGuy, false).queue(
                                                        (e) -> event.reply("smutato " + surelyTheGuy.getAsMention()), 
                                                        new ErrorHandler().handle(
                                                            ErrorResponse.MISSING_PERMISSIONS,
                                                                (e) -> event.replyError("sorry, " + e.getMessage()))
                );
            } else
                event.reply("Brutto fallito non kickare se non sei admin UwU");
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}
