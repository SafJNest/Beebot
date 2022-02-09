package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Connect extends Command {

    public Connect(){
        this.name = "connect";
        this.aliases = new String[]{"join", "comeherebroda"};
        this.help = "Il bot si connette nel tuo canale vocale";
    }

	@Override
	protected void execute(CommandEvent event) {
        if(event.getMember().getVoiceState().getChannel() == null)
            event.reply("Non sei in un canale vocale.");
        else
		    event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
	}
}