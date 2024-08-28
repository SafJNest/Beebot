package com.safjnest.commands.audio.slash.playlist;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class PlaylistSlash extends SlashCommand {

    public PlaylistSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        this.children = new SlashCommand[]{
            new PlaylistCreateSlash(father),
            new PlaylistAddSongSlash(father),
            new PlaylistAddQueueSlash(father),
            new PlaylistPlaySlash(father),
            new PlaylistRemoveSongSlash(father),
            new PlaylistDeleteSlash(father),
            new PlaylistListSlash(father),
            new PlaylistViewSlash(father)
        };

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }
}