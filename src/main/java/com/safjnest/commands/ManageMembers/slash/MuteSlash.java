package com.safjnest.commands.ManageMembers.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

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
public class MuteSlash extends SlashCommand{

    public MuteSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.botPermissions = new Permission[]{Permission.VOICE_MUTE_OTHERS};
        this.userPermissions = new Permission[]{Permission.VOICE_MUTE_OTHERS};
        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "member", "Member to mute", true));

        commandData.setThings(this);
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