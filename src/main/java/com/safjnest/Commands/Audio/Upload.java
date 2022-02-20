package com.safjnest.Commands.Audio;

import java.util.EventListener;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.FileListener;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Upload extends Command{

    public Upload(){
        this.name = "upload";
        this.aliases = new String[]{"up"};
        this.help = "Il bot si connette nel tuo canale vocale\nSe già connesso in un altro canale sarà disconnesso dallo stesso.\n"
        + "In caso tutti gli utenti escano dalla stanza il bot si disconnetterà automaticamente.";
        this.category = new Category("Audio");
        this.arguments = "null";
    }

	@Override
	protected void execute(CommandEvent event) {
        event.reply("operativo e pronto a listenare");
        FileListener listino = new FileListener(event.getArgs());
        event.getJDA().addEventListener(listino);
	}
}
