package com.safjnest.commands.audio.slash.soundboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.model.sound.Sound;
import com.safjnest.sql.DatabaseHandler;
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
public class SoundboardCreateSlash extends SlashCommand{
    private static final int maxSounds = 20;

    public SoundboardCreateSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();
        this.options.add(new OptionData(OptionType.STRING, "name", "Leave blank to not save the soundboard.", false));
        for(int i = 1; i <= maxSounds; i++) {
            this.options.add(new OptionData(OptionType.STRING, "sound-" + i, "Sound " + i, false).setAutoComplete(true));
        }
        
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        Set<String> soundIDs = new HashSet<String>();
        for(OptionMapping option : event.getOptions())
            if(option != null && !option.getName().equals("name"))
                soundIDs.add(option.getAsString());

        if(soundIDs.isEmpty()) {
            event.deferReply(true).addContent("You need to insert at least a sound.").queue();
            return;
        }

        String soundboardName = "temporary";
        if(event.getOption("name") != null) {
            soundboardName = event.getOption("name").getAsString();
            if(DatabaseHandler.soundboardExists(soundboardName, event.getGuild().getId())) {
                event.deferReply(true).addContent("A soundboard with that name in this guild already exists.").queue();
                return;
            }
            DatabaseHandler.insertSoundBoard(soundboardName, event.getGuild().getId(), event.getUser().getId(), soundIDs.toArray(new String[0]));
        }

        List<Sound> sounds = SoundHandler.getSoundsByIds(soundIDs.toArray(new String[0]));
        SoundHandler.composeSoundboard(event, soundboardName, sounds).queue();

        

    }    
}