package com.safjnest.bot.commands.audio.soundboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class SoundboardAdd extends SlashCommand{
    private static final int maxSounds = BotDB.MAX_SOUNDBOARD_SOUNDS;

    public SoundboardAdd(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();
        this.options.add(new OptionData(OptionType.STRING, "soundboard_name", "Soundboard to add the sound(s) to.", true).setAutoComplete(true));

        int soundOptions = Math.min(maxSounds, CommandData.MAX_OPTIONS - this.options.size());
        for (int i = 1; i <= soundOptions; i++)
            this.options.add(new OptionData(OptionType.STRING, "sound-" + i, "Sound " + i, false).setAutoComplete(true));

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String boardId = event.getOption("soundboard_name").getAsString();
        Set<String> ids = new LinkedHashSet<>();
        for (OptionMapping option : event.getOptions()) {
            if (option.getName().startsWith("sound-"))
                ids.add(option.getAsString());
        }
        if (ids.isEmpty()) {
            event.deferReply(true).addContent("You need to insert at least a sound.").queue();
            return;
        }

        try {
            BotDB.get().addSoundsToBoard(boardId, Soundboard.editAccess(event, boardId), ids);
            event.deferReply(false).addContent("Sound added correctly").queue();
        } catch (Exception failure) {
            event.deferReply(true).addContent(failure.getMessage()).queue();
        }
    }
}
