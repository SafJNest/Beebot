package com.safjnest.commands.audio.playlist;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class Playlist extends SlashCommand {

    public Playlist() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        this.children = new SlashCommand[]{
            new PlaylistCreate(father),
            new PlaylistAddSong(father),
            new PlaylistAddQueue(father),
            new PlaylistPlay(father),
            new PlaylistRemoveSong(father),
            new PlaylistDelete(father),
            new PlaylistList(father),
            new PlaylistView(father)
        };

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
    }
}