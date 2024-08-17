package com.safjnest.commands.members;

import java.util.concurrent.TimeUnit;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Ban extends Command{

    public Ban(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            String[] args = event.getArgs().split(" ", 2);
            String mentionedName = args[0];
            String reason = (args.length < 2) ? "unspecified reason" : args[1];

            Member selfMember = event.getGuild().getSelfMember();
            Member author = event.getMember();
            Member mentionedMember = PermissionHandler.getMentionedMember(event, mentionedName);
            
            if(mentionedMember == null) { 
                event.reply("Couldn't find the specified member, please mention or write the id of a member. After that you can also add a reaseon for the ban.");
            }// if you mention a user not in the guild or write a wrong id

            else if(!selfMember.canInteract(mentionedMember)) {
                event.reply(selfMember.getAsMention() + " can't ban a member with higher or equal highest role than itself.");
            }// if the bot doesnt have a high enough role to ban the member

            else if(!author.canInteract(mentionedMember) || author == mentionedMember) {
                event.reply("You can't ban a member with higher or equal highest role than yourself.");
            }// if the author doesnt have a high enough role to ban the member and if its not yourself!
            
            else{
                event.getGuild().ban(mentionedMember,  0, TimeUnit.SECONDS).reason(reason).queue(
                    (e) -> event.reply(mentionedMember.getAsMention() + " has been banned"), 
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.replyError("Error. " + e.getMessage()))
                );
            }
        } catch (Exception e) {
            event.replyError("Error: " + e.getMessage());
        }
    }
}