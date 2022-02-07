package com.safjnest.Commands;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

public class Clear extends Command {

    public Clear(){
        this.name = "clear";
        this.aliases = new String[]{"cancella"};
        this.help = "il bot cancella i messaggi come un frake";
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