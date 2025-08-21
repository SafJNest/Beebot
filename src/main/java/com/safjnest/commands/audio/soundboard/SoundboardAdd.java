package com.safjnest.commands.audio.soundboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class SoundboardAdd extends SlashCommand{
    private static final int maxSounds = 20;

    public SoundboardAdd(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();
        this.options.add(new OptionData(OptionType.STRING, "soundboard_name", "Soundboard to add the sound(s) to.", true).setAutoComplete(true));
        for(int i = 1; i <= maxSounds-1; i++) {
            this.options.add(new OptionData(OptionType.STRING, "sound-" + i, "Sound " + i, false).setAutoComplete(true));
        }

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        Set<String> soundIDs = new HashSet<String>();
        for(OptionMapping option : event.getOptions())
            if(option != null && !option.getName().equals("soundboard_name"))
                soundIDs.add(option.getAsString());

        if(soundIDs.isEmpty()) {
            event.deferReply(true).addContent("You need to insert at least a sound.").queue();
            return;
        }

        String soundboardName = event.getOption("soundboard_name").getAsString();
        if(!BotDB.soundboardExists(soundboardName, event.getGuild().getId(), event.getUser().getId())) {
            event.deferReply(true).addContent("A soundboard with that name does not exist in this guild.").queue();
            return;
        }

        String soundboardID = event.getOption("soundboard_name").getAsString();
        int soundCount = BotDB.getSoundInSoundboardCount(soundboardID);

        if(soundCount >= maxSounds) {
            event.deferReply(true).addContent("The soundboard is already full.").queue();
            return;
        }

        if(soundCount + soundIDs.size() >= maxSounds) {
            event.deferReply(true).addContent("Too many sounds, the soundboard contains " + soundCount + "/" + maxSounds + " sounds and you tried to add " + soundIDs.size() + " sounds.").queue();
            return;
        }

        BotDB.insertSoundsInSoundBoard(soundboardID, soundIDs.toArray(new String[0]));

        event.deferReply(false).addContent("Sound added correctly").queue();
    }    
}