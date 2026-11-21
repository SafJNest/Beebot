package com.safjnest.bot.commands.audio.soundboard;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 3.0
 */
public class SoundboardRemove extends SlashCommand{

    public SoundboardRemove(String father){
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
        String soundId = event.getOption("sound_remove").getAsString();
        String boardId = event.getOption("name").getAsString();

        try {
            BotDB.get().removeSoundFromBoard(boardId, Soundboard.editAccess(event, boardId), soundId);
            event.deferReply(false).addContent("Sound deleted correctly.").queue();
        } catch (Exception failure) {
            event.deferReply(true).addContent(failure.getMessage()).queue();
        }
    }
}
