package com.safjnest.Commands.ManageMembers;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class UnMute extends Command{

    public UnMute(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        String mentionedName = event.getArgs();

        if(mentionedName == ""){
            event.reply("Member missing, please mention or write the id of a member");
            return;
        }

        try {
            Member selfMember = event.getGuild().getSelfMember();
            Member author = event.getMember();
            Member mentionedMember = PermissionHandler.getMentionedMember(event, mentionedName);

            if(mentionedMember == null) { 
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            }// if you mention a user not in the guild or write a wrong id

            else if(!selfMember.hasPermission(Permission.VOICE_MUTE_OTHERS)) {
                event.reply(selfMember.getAsMention() + " doesn't have the permission to unmute members, give the bot a role that can do that.");
            }// if the bot doesnt have the VOICE_MUTE_OTHERS permission

            else if(!selfMember.canInteract(mentionedMember)) {
                event.reply(selfMember.getAsMention() + " can't unmute a member with higher or equal highest role than itself.");
            }// if the bot doesnt have a high enough role to unmute the member 

            else if(!author.hasPermission(Permission.VOICE_MUTE_OTHERS)) {
                event.reply("You don't have the permission to unmute.");
            }// if the author doesnt have the VOICE_MUTE_OTHERS permission

            else if(!author.canInteract(mentionedMember) && author != mentionedMember) {
                event.reply("You can't unmute a member with higher or equal highest role than yourself.");
            }// if the author doesnt have a high enough role to unmute the member and if its not yourself!

            else if(!mentionedMember.getVoiceState().isMuted()) {
                event.reply("Member is already unmuted.");
            }// if the member is already unmuted
            
            else {
                event.getGuild().mute(mentionedMember, false).queue(
                    (e) -> event.reply(mentionedMember.getAsMention() + " has been unmuted"), 
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.replyError("Error. " + e.getMessage()))
                );
            }
        } catch (Exception e) {
            event.replyError("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}