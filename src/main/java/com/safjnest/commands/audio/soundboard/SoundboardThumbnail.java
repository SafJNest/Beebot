package com.safjnest.commands.audio.soundboard;

import java.util.ArrayList;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class SoundboardThumbnail extends SlashCommand{

    public SoundboardThumbnail(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();
        this.options.add(new OptionData(OptionType.STRING, "soundboard_name", "Soundboard to add the sound(s) to.", true).setAutoComplete(true));
        this.options.add(new OptionData(OptionType.ATTACHMENT, "thumbnail", "Thumbnail for the soundboard.", true));

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String soundboardName = event.getOption("soundboard_name").getAsString();

        if(!BotDB.soundboardExists(soundboardName, event.getGuild().getId(), event.getUser().getId())) {
            event.deferReply(true).addContent("Soundboard does not exist or you dont have permission to play the selected one.").queue();
            return;
        }

        String soundboardID = event.getOption("soundboard_name").getAsString();
        Attachment thumbnail = event.getOption("thumbnail").getAsAttachment();
        if (!SoundEmbed.isValidThumbnail(thumbnail)) {
            event.deferReply(true).addContent("Invalid thumbnail.").queue();
            return;
        }
        if (!BotDB.updateSoundboardThumbnail(soundboardID, thumbnail)) {
            event.deferReply(true).addContent("Error updating thumbnail.").queue();
            return;
        }

        event.deferReply(false).addContent("Thumbnail updated correctly.").queue();
    }    
}