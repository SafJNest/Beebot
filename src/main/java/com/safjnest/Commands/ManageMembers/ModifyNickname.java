package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ModifyNickname extends Command {
    /**
     * Default constructor for the class.
     */
    public ModifyNickname() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] args = event.getArgs().split(" ", 2);

        if(args[0] == "") {
            event.reply("Member missing, please mention or write the id of a member");
            return;
        }
        if(args.length < 2) {
            event.reply("New nickname missing, please write the new nickname after the user.");
            return;
        }

        try {
            String mentionedName = args[0];
            String newNickname = args[1];

            Member selfMember = event.getGuild().getSelfMember();
            Member author = event.getMember();
            Member mentionedMember = PermissionHandler.getMentionedMember(event, mentionedName);
            
            if(mentionedMember == null) { 
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            }// if you mention a user not in the guild or write a wrong id

            else if(newNickname.length() > 32) {
                event.reply("The new nickname must be 32 or fewer in lenght.");
            }// if the nickname is longer than 32 characters

            else if(!selfMember.hasPermission(Permission.NICKNAME_MANAGE)) {
                event.reply(selfMember.getAsMention() + " doesn't have the permission to change nicknames, give the bot a role that can do that.");
            }// if the bot doesnt have the NICKNAME_MANAGE permission

            else if(!selfMember.canInteract(mentionedMember)) {
                event.reply(selfMember.getAsMention() + " can't change the nickname of a member with higher or equal highest role than itself.");
            }// if the bot doesnt have a high enough role to change the nickname of the member

            else if(!author.hasPermission(Permission.NICKNAME_MANAGE)) {
                event.reply("You don't have the permission to change nicknames.");
            }// if the author doesnt have the NICKNAME_MANAGE permission

            else if(!author.canInteract(mentionedMember) && author != mentionedMember) {
                event.reply("You can't change the nickname of a member with higher or equal highest role than yourself.");
            }// if the author doesnt have a high enough role to change the nickname of the member and if its not yourself!
            
            else {
                mentionedMember.modifyNickname(newNickname).queue(
                    (e) -> event.reply("Changed nickname of " + mentionedMember.getAsMention()), 
                    new ErrorHandler()
                        .handle(
                            ErrorResponse.MISSING_PERMISSIONS, 
                            (e) -> event.replyError("Error. " + e.getMessage())
                        )
                        .handle(
                            ErrorResponse.INVALID_FORM_BODY, 
                            (e) -> event.replyError("Error. " + e.getMessage())
                        )
                );
            }
        } catch (Exception e) {
            event.replyError("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}