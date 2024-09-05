package com.safjnest.commands.members;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Image extends SlashCommand{
    public Image(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User from which to take the profile picture", true)
        );
        
        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            User mentionedUser;
            if(event.getArgs().equals(""))
                mentionedUser = event.getAuthor();
            else
                mentionedUser = PermissionHandler.getMentionedUser(event, event.getArgs());

            if(mentionedUser == null)
                event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            else
                event.reply(mentionedUser.getAvatarUrl() + "?size=4096&quality=lossless");
        } catch (Exception e) {
            event.reply("Error: " + e.getMessage());
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            User mentionedUser = event.getOption("user").getAsUser();
            event.deferReply(false).addContent(mentionedUser.getAvatarUrl() + "?size=4096").queue();
        } catch (Exception e) {
            event.deferReply(true).addContent("Error: " + e.getMessage()).queue();
        }
    }
}