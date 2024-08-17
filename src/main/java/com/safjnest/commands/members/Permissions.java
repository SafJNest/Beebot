package com.safjnest.commands.members;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Permissions extends Command{
    public Permissions(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            Member mentionedMember;
            if(event.getArgs().equals(""))
                mentionedMember = event.getMember();
            else
                mentionedMember = PermissionHandler.getMentionedMember(event, event.getArgs());

            if(mentionedMember == null) {
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
                return;
            }

            if (mentionedMember.isOwner()) {
                event.reply(mentionedMember.getAsMention() + " is the owner of the guild.");
            }
            else if (mentionedMember.hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(mentionedMember.getAsMention() + " is an admin.");
            }
            else {
                StringBuilder permissionsString = new StringBuilder();
                for(Permission permission :  mentionedMember.getPermissions())
                    permissionsString.append(permission.getName() + " - ");
                permissionsString.delete(permissionsString.length() - 3, permissionsString.length());
                event.reply(mentionedMember.getAsMention() + " **is not an admin and these are his permissions:** \n" + permissionsString.toString());
            }
        } catch (Exception e) {
            event.reply("Error: " + e.getMessage());
        }
    }
}