package com.safjnest.Commands;

import java.util.List;
import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;

public class Clear extends Command {
    private static Set<String> untouchables;
    public Clear(Set<String> untouchables){
        this.name = "clear";
        this.aliases = new String[]{"cancella"};
        this.help = "il bot cancella i messaggi come un frake";
        this.untouchables = untouchables;
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        MessageChannel channel = event.getChannel();
        if (!hasPermission(event.getMember(), Permission.MESSAGE_MANAGE))
        channel.sendMessage("im so sorry non sei admin non rompere il cazzo :D").queue();
        if(Integer.parseInt(commandArray[1]) > 99){
            channel.sendMessage("Puoi cancellare massimo 100 messaggi alla volta, quindi 99 + il comando = 100");
        }
        TextChannel chan = event.getTextChannel();
        MessageHistory history = new MessageHistory(channel);
        List<Message> msgs;
        msgs = history.retrievePast(Integer.parseInt(commandArray[1])+ 1).complete();
        chan.deleteMessages(msgs).queue();
	}

    public static boolean hasPermission(Member theGuy, Permission permission) {
        if (theGuy.hasPermission(permission) || untouchables.contains(theGuy.getId()))
            return true;
        return false;
    }
    


}
