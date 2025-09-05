package com.safjnest.commands.members;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Permissions extends SlashCommand {
    public Permissions(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "member", "Member to get the permission of", true));

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

    @Override
    protected void execute(SlashCommandEvent event) {
        Member mentionedMember = (event.getOption("member") == null) ? event.getMember() : event.getOption("member").getAsMember();
        try {
            if (mentionedMember.isOwner()) {
                event.deferReply(false).addContent(mentionedMember.getAsMention() + " is the owner of the guild.").queue();
            }
            else if (mentionedMember.hasPermission(Permission.ADMINISTRATOR)) {
                event.deferReply(false).addContent(mentionedMember.getAsMention() + " is an admin.").queue();
            }
            else {
                StringBuilder permissionsString = new StringBuilder();
                for(Permission permission :  mentionedMember.getPermissions())
                    permissionsString.append(permission.getName() + " - ");
                permissionsString.delete(permissionsString.length() - 3, permissionsString.length());
                event.deferReply(false).addContent(mentionedMember.getAsMention() + " **is not an admin and these are his permissions:** \n" + permissionsString.toString()).queue();
            }
        } catch (Exception e) {
            event.deferReply(true).addContent("Error: " + e.getMessage()).queue();
        }
    }
}