package com.safjnest.commands.audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;


/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Connect extends Command {

    public Connect(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

	@Override
	protected void execute(CommandEvent event) {
        if(event.getMember().getVoiceState().getChannel() == null) {
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

		event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
	}
}