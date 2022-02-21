package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.FileListener;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class Upload extends Command{

    public Upload(){
        this.name = "upload";
        this.aliases = new String[]{"up", "add"};
        this.help = "Il comando consente di poter caricare facilmente dei suoni nel database del bot.\nSe carichi dei file mp3 ci fai un piacere.\n"
        + "Se carichi dei .opus ti sgozzo.";
        this.category = new Category("Audio");
        this.arguments = "[upload] [nome del suono, senza specificare il formato]";
    }
    
	@Override
	protected void execute(CommandEvent event) {
        event.reply("operativo e pronto a listenare");
        FileListener listino = new FileListener(event.getArgs());
        event.getJDA().addEventListener(listino);
	}
}
