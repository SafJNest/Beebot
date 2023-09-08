package com.safjnest.SlashCommands.ManageMembers;

import java.util.Arrays;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class UnbanSlash extends SlashCommand{

    public UnbanSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User (ID) to ban , omit to get the ban list", false));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            Member selfMember = event.getGuild().getSelfMember();
            if(!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                event.deferReply(true).addContent(selfMember.getAsMention() + " doesn't have the permission to unban users, give the bot a role that can do that.").queue();
                return;
            }// if the bot doesnt have the BAN_MEMBERS permission it cant see or unban the banned users

            if(event.getOption("user") == null) {
                StringBuilder unbans = new StringBuilder();

                unbans.append("**List of banned users:**\n");
                for (net.dv8tion.jda.api.entities.Guild.Ban ban : event.getGuild().retrieveBanList().complete())
                    unbans.append(ban.getUser().getAsMention() + " - ");
                unbans.delete(unbans.length() - 3, unbans.length());

                event.deferReply(false).addContent(unbans.toString()).queue();
            }
            else {
                User mentionedUser = event.getOption("user").getAsUser();

                Member author = event.getMember();
                
                if(mentionedUser == null) { 
                    event.deferReply(true).addContent("Couldn't find the specified user, please write the id of a banned user.").queue();
                }// if you mention a user not in the guild or write a wrong id

                else if(!PermissionHandler.isUserBanned(event.getGuild(), mentionedUser)) {
                    event.deferReply(true).addContent("The user is not banned from this guild.").queue();
                }// if the user is not banned from the guild

                else if(!author.hasPermission(Permission.BAN_MEMBERS)) {
                    event.deferReply(true).addContent("You don't have the permission to unban.").queue();
                }// if the author doesnt have the BAN_MEMBERS permission

                else {
                    event.getGuild().unban(mentionedUser).queue(
                        (e) -> event.deferReply(false).addContent(mentionedUser.getAsMention() + " has been unbanned").queue(), 
                        new ErrorHandler().handle(
                            ErrorResponse.MISSING_PERMISSIONS,
                            (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
                    );
                }
            }
        } catch (Exception e) {
            event.deferReply(true).addContent("Error: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }
}