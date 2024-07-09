package com.safjnest.commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Image extends Command{
    public Image(){
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
}