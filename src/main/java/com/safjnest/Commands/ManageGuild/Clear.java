package com.safjnest.Commands.ManageGuild;

import java.util.List;
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
        this.name = "clear";
        this.aliases = new String[]{"cancella"};
        this.help = "Il bot cancella i messaggi in un canale di testo (max 99)";
        this.category = new Category("ServerManage");
        this.arguments = "[clear] [n messaggi]";
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        if (!PermissionHandler.hasPermission(event.getMember(), Permission.MESSAGE_MANAGE))
            event.reply("im so sorry non sei admin non rompere il cazzo :D");
        if(Integer.parseInt(commandArray[1]) > 99)
            event.reply("Puoi cancellare massimo 100 messaggi alla volta, quindi 99 + il comando = 100");
        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(Integer.parseInt(commandArray[1])+ 1).complete();
        event.getTextChannel().deleteMessages(msgs).queue();
	}
}