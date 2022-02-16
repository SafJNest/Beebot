package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
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