package com.safjnest.commands.audio.soundboard;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.sql.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class Soundboard extends SlashCommand{

    public Soundboard(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.aliases = commandData.getAliases();


        String father = this.getClass().getSimpleName().replace("Slash", "");

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new SoundboardCreate(father), new SoundboardPlay(father), new SoundboardAdd(father), new SoundboardRemove(father), new SoundboardDelete(father), new SoundboardThumbnail(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
        
        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }

    @Override
    public void execute(CommandEvent event) {
        String soundboardID = BotDB.searchSoundboard(event.getArgs(), event.getGuild().getId(), event.getAuthor().getId()).get("id");
        if(soundboardID == null || soundboardID.isEmpty() || !BotDB.soundboardExists(soundboardID, event.getGuild().getId(), event.getAuthor().getId())) {
            event.reply("Soundboard does not exist or you dont have permission to play the selected one.");
            return;
        }
        SoundEmbed.composeSoundboard(event, soundboardID);
    }
}