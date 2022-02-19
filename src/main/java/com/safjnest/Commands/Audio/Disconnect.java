package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Disconnect extends Command {

    public Disconnect(){
        this.name = "disconnect";
        this.aliases = new String[]{"bye", "levatidalcazzo"};
        this.help = "Il bot si disconnette dal canale vocale in cui è attualmente connesso.\nÈ possibile disconnettere il bot solo se si è connessi nella sua stessa stanza(serve per non dare fastidio).";
        this.category = new Category("Audio");
        this.arguments = "null";
    }

	@Override
	protected void execute(CommandEvent event) {
		event.getGuild().getAudioManager().closeAudioConnection();
	}
    


}