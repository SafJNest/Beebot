package com.safjnest.SlashCommands.ManageMembers;

import com.safjnest.Utilities.CommandsLoader;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
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
public class UnMuteSlash extends SlashCommand{

    public UnMuteSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "member", "Member to unmute", true));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            Member mentionedMember = event.getOption("member").getAsMember();

            Member selfMember = event.getGuild().getSelfMember();
            Member author = event.getMember();

            if(mentionedMember == null) { 
                event.deferReply(true).addContent("Couldn't find the specified member, please mention or write the id of a member.").queue();
            }// if you mention a user not in the guild or write a wrong id

            else if(!selfMember.hasPermission(Permission.VOICE_MUTE_OTHERS)) {
                event.deferReply(true).addContent(selfMember.getAsMention() + " doesn't have the permission to unmute members, give the bot a role that can do that.").queue();
            }// if the bot doesnt have the VOICE_MUTE_OTHERS permission

            else if(!selfMember.canInteract(mentionedMember)) {
                event.deferReply(true).addContent(selfMember.getAsMention() + " can't unmute a member with higher or equal highest role than itself.").queue();
            }// if the bot doesnt have a high enough role to unmute the member 

            else if(!author.hasPermission(Permission.VOICE_MUTE_OTHERS)) {
                event.deferReply(true).addContent("You don't have the permission to unmute.").queue();
            }// if the author doesnt have the VOICE_MUTE_OTHERS permission

            else if(!author.canInteract(mentionedMember) && author != mentionedMember) {
                event.deferReply(true).addContent("You can't unmute a member with higher or equal highest role than yourself.").queue();
            }// if the author doesnt have a high enough role to unmute the member and if its not yourself!

            else if(!mentionedMember.getVoiceState().isMuted()) {
                event.deferReply(true).addContent("Member is already unmuted.").queue();
            }// if the member is already unmuted

            else {
                event.getGuild().mute(mentionedMember, false).queue(
                    (e) -> event.deferReply(false).addContent(mentionedMember.getAsMention() + " has been unmuted").queue(), 
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
                );
            }
        } catch (Exception e) {
            event.deferReply(true).addContent("Error: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }
}
