package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
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
            SelfUser selfuser = event.getJDA().getSelfUser();
            Member selfMember = event.getGuild().getMember(selfuser);
            Member author = event.getMember();
            User mentionedUser = PermissionHandler.getMentionedUser(event, mentionedName);
            Member mentionedMember = null;
            try {
                mentionedMember = event.getGuild().getMember(mentionedUser);
            } catch (Exception e) {}
            
            if(mentionedMember == null) { 
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            }// if you mention a user not in the guild or write a wrong id

            else if(!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
                event.reply(selfuser.getAsMention() + " doesn't have the permission to kick members, give the bot a role that can do that.");
            }// if the bot doesnt have the KICK_MEMBERS permission

            else if(PermissionHandler.isUntouchable(mentionedUser.getId())) {
                event.reply("Don't you dare touch my creators.");
            }// well...

            else if(!selfMember.canInteract(mentionedMember)) {
                event.reply(selfuser.getAsMention() + " can't kick a member with higher or equal highest role than itself.");
            }// if the bot doesnt have a high enough role to kick the member

            else if(!author.hasPermission(Permission.KICK_MEMBERS)) {
                event.reply("You don't have the permission to kick.");
            }// if the author doesnt have the KICK_MEMBERS permission

            else if(!author.canInteract(mentionedMember) && author != mentionedMember) {
                event.reply("You can't kick a member with higher or equal highest role than yourself.");
            }// if the author doesnt have a high enough role to kick the member and if its not yourself!
            
            else {
                event.getGuild().kick(mentionedUser).queue(
                    (e) -> event.reply(mentionedUser.getAsMention() + " has been kicked"), 
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
