package com.safjnest.Commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Disconnect extends Command {

    public Disconnect(){
        this.name = "disconnect";
        this.aliases = new String[]{"bye", "levatidalcazzo"};
        this.help = "il bot si disconnette e ti banna";
    }

	@Override
	protected void execute(CommandEvent event) {
        //TODO fix
		event.getGuild().getAudioManager().closeAudioConnection();
	}
    


}