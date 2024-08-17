package com.safjnest.commands.members.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class ImageSlash extends SlashCommand{
    public ImageSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User from which to take the profile picture", true)
        );
        
        commandData.setThings(this);
            
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