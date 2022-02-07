package com.safjnest.Commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Connect extends Command {

    public Connect(){
        this.name = "connect";
        this.aliases = new String[]{"join", "comeherebroda"};
        this.help = "il bot si connette e ti outplaya";
    }

	@Override
	protected void execute(CommandEvent event) {
        /* fix
        */
		event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
	}
    


}
