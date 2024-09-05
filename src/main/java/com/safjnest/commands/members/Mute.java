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
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Mute extends SlashCommand {

    public Mute(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.botPermissions = new Permission[]{Permission.VOICE_MUTE_OTHERS};
        this.userPermissions = new Permission[]{Permission.VOICE_MUTE_OTHERS};

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "member", "Member to mute", true));

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            String mentionedName = event.getArgs();

            Member selfMember = event.getGuild().getSelfMember();
            Member author = event.getMember();
            Member mentionedMember = PermissionHandler.getMentionedMember(event, mentionedName);
            
            if(mentionedMember == null) { 
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            }// if you mention a user not in the guild or write a wrong id

            else if(!selfMember.canInteract(mentionedMember)) {
                event.reply(selfMember.getAsMention() + " can't mute a member with higher or equal highest role than itself.");
            }// if the bot doesnt have a high enough role to mute the member 

            else if(!author.canInteract(mentionedMember) && author != mentionedMember) {
                event.reply("You can't mute a member with higher or equal highest role than yourself.");
            }// if the author doesnt have a high enough role to mute the member and if its not yourself!

            else if(mentionedMember.getVoiceState().isMuted()) {
                event.reply("Member is already muted.");
            }// if the member is already muted
            
            else {
                event.getGuild().mute(mentionedMember, true).queue(
                    (e) -> event.reply(mentionedMember.getAsMention() + " has been muted"), 
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.replyError("Error. " + e.getMessage()))
                );
            }
        } catch (Exception e) {
            event.replyError("Error: " + e.getMessage());
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            Member mentionedMember = event.getOption("member").getAsMember();

            if(mentionedMember == null) { 
                event.deferReply(true).addContent("Couldn't find the specified member, please mention or write the id of a member.").queue();
            }// if you mention a user not in the guild or write a wrong id

            else if(mentionedMember.getVoiceState().isMuted()) {
                event.deferReply(true).addContent("Member is already muted.").queue();
            }// if the member is already muted

            else {
                event.getGuild().mute(mentionedMember, true).queue(
                    (e) -> event.deferReply(false).addContent(mentionedMember.getAsMention() + " has been muted").queue(), 
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
                );
            }
        } catch (Exception e) {
            event.deferReply(true).addContent("Error: " + e.getMessage()).queue();
        }
    }
}