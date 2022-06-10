package com.safjnest.Commands.ManageGuild;

import java.util.List;

import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PermissionHandler;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

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
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        if (!PermissionHandler.hasPermission(event.getMember(), Permission.MESSAGE_MANAGE)){
            event.reply("im so sorry non sei admin non rompere le scatole :D");
            return;
        }
        if(Integer.parseInt(commandArray[1]) > 99){
            event.reply("Puoi cancellare massimo 100 messaggi alla volta, quindi 99 + il comando = 100");
            return;
        }
        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(Integer.parseInt(commandArray[1])+ 1).complete();
        event.getTextChannel().deleteMessages(msgs).queue();
	}
}