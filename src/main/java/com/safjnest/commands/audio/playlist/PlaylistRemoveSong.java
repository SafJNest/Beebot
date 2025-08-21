package com.safjnest.commands.audio.playlist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistRemoveSong extends SlashCommand {

    public PlaylistRemoveSong(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of the custom playlist", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "playlist-song", "Name of the song to remove", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();

        int playlistId = event.getOption("playlist-name").getAsInt();
        int songId = event.getOption("playlist-song").getAsInt();

        int result = BotDB.deletePlaylistSong(playlistId, songId, event.getUser().getId());

        if(result == 0) event.getHook().editOriginal("Playlist not found.").queue();
        else if(result == -1) event.getHook().editOriginal("You are not the creator of this playlist.").queue();
        else if(result == -2) event.getHook().editOriginal("An error occurred.").queue();
        else event.getHook().editOriginal("Removed song from playlist.").queue();
    }
}
