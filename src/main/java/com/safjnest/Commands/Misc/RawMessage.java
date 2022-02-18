package com.safjnest.Commands.Misc;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

public class RawMessage extends Command{

    public RawMessage(){
        this.name = "getlastrawmessage";
        this.aliases = new String[]{"rawmsg"};
        this.help = "Restituisce il contenuto dell'ultimo messaggio mandato";
    }

    @Override
    protected void execute(CommandEvent event) {
        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(2).complete();
        event.reply(msgs.get(1).getContentRaw());
    }
}
