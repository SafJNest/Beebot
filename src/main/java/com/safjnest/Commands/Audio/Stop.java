package com.safjnest.Commands.Audio;

import java.util.EventListener;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Stop extends Command{

    public Stop() {}{
        this.name = "stop";
        this.aliases = new String[]{"basta", "s"};
        this.help = "Il bot si connette nel tuo canale vocale\nSe già connesso in un altro canale sarà disconnesso dallo stesso.\n"
        + "In caso tutti gli utenti escano dalla stanza il bot si disconnetterà automaticamente.";
        this.category = new Category("Audio");
        this.arguments = "null";
    }

	@Override
	protected void execute(CommandEvent event) {
        

	}

    
    public void onMessageReceived(MessageReceivedEvent e){
        System.out.println(e.getMessage().getAttachments().get(0).getFileExtension());
    }
}