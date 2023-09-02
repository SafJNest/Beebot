package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Unban extends Command{

    public Unban(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    public static boolean isUserBanned(Guild guild, User user) throws InsufficientPermissionException{
        try {
            guild.retrieveBan(user).complete();
            return true;
        } catch (ErrorResponseException e) {
            return false;
        }
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            if(event.getArgs().length() == 0) {
                SelfUser selfuser = event.getJDA().getSelfUser();
                Member selfMember = event.getGuild().getMember(selfuser);

                if(!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                    event.reply(selfuser.getAsMention() + " doesn't have the permission to unban users so it cant see the banned users, give the bot a role that can do that.");
                }// if the bot doesnt have the BAN_MEMBERS permission it cant see the banned users

                else {
                    StringBuilder unbans = new StringBuilder();

                    unbans.append("**List of banned users:**\n");
                    for (net.dv8tion.jda.api.entities.Guild.Ban ban : event.getGuild().retrieveBanList().complete())
                        unbans.append(ban.getUser().getAsMention() + " - ");
                    unbans.delete(unbans.length() - 3, unbans.length());

                    event.reply(unbans.toString());
                }
            }
            else {
                SelfUser selfuser = event.getJDA().getSelfUser();
                Member selfMember = event.getGuild().getMember(selfuser);
                Member author = event.getMember();
                User mentionedUser = PermissionHandler.getMentionedUser(event, event.getArgs());
                
                Member mentionedMember = null;
                try {
                    mentionedMember = event.getGuild().getMember(mentionedUser);
                } catch (Exception e) {}

                if(mentionedMember == null) { 
                    event.reply("Couldn't find the specified member, please write the id of a banned user. You can also use unban without parameters to get a list of banned members.");
                }// if you mention a user not in the guild or write a wrong id

                else if(!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                    event.reply(selfuser.getAsMention() + " doesn't have the permission to unban users, give the bot a role that can do that.");
                }// if the bot doesnt have the BAN_MEMBERS permission

                else if(!isUserBanned(event.getGuild(), mentionedUser)) {
                    event.reply("The user is not banned from this guild.");
                }// if the user is not banned from the guild

                else if(!author.hasPermission(Permission.BAN_MEMBERS)) {
                    event.reply("You don't have the permission to unban.");
                }// if the author doesnt have the BAN_MEMBERS permission

                else {
                    event.getGuild().unban(mentionedUser).queue(
                        (e) -> event.reply(mentionedUser.getAsMention() + " has been unbanned"), 
                        new ErrorHandler().handle(
                            ErrorResponse.MISSING_PERMISSIONS,
                            (e) -> event.replyError("Error. " + e.getMessage()))
                    );
                }
            }
        } catch (Exception e) {
            event.replyError("Error: " + e.getMessage());
        }
    }
}