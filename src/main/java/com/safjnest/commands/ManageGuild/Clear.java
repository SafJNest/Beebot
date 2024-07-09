package com.safjnest.commands.ManageGuild;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Clear extends Command {

    public Clear(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.botPermissions = new Permission[]{Permission.MESSAGE_MANAGE};
        this.userPermissions = new Permission[]{Permission.MESSAGE_MANAGE};

        commandData.setThings(this);
    }

	@Override
	protected void execute(CommandEvent event) {
        if(!SafJNest.intIsParsable(event.getArgs())) {
            event.reply("Specify how many messages to delete (max 99).");
            return;
        }
        int n = Integer.parseInt(event.getArgs());

        if(n > 99){
            event.reply("You can't delete more than 99 messages at once.");
            return;
        }

        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(n + 1).complete();
        event.getTextChannel().deleteMessages(msgs).queue();
	}
}