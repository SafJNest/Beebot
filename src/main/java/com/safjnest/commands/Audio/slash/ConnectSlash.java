package com.safjnest.commands.Audio.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class ConnectSlash extends SlashCommand {

    public ConnectSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        if(event.getMember().getVoiceState().getChannel() == null){
            event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

		event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
	}
}