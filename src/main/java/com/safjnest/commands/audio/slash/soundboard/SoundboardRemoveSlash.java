package com.safjnest.commands.audio.slash.soundboard;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 3.0
 */
public class SoundboardRemoveSlash extends SlashCommand{

    public SoundboardRemoveSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Name of soundboard to change", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound_remove", "Sound to remove", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String soundID = event.getOption("sound_remove").getAsString();
        String soundboardID = event.getOption("name").getAsString();

        if(!DatabaseHandler.deleteSoundFromSoundboard(soundboardID, soundID)){
            event.deferReply(false).addContent("Error deleting sound.").queue();
            return;
        }
        if(DatabaseHandler.getSoundInSoundboardCount(soundboardID) == 1){
            DatabaseHandler.deleteSoundboard(soundboardID);
            event.deferReply(false).addContent("The soundboard has been deleted because it was empty.").queue();
            return;
        }
        event.deferReply(false).addContent("Sound deleted correctly.").queue();
    }    
}
