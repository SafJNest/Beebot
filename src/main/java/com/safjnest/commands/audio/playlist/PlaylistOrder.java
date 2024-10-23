package com.safjnest.commands.audio.playlist;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistOrder extends SlashCommand {

    public PlaylistOrder(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of the custom playlist", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "playlist-song", "Name of the song to order", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "playlist-order", "Position or song to replace", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "order", "How to order the songs", true)
                .addChoice("Swap", "swap")
                .addChoice("Move", "move")
                .addChoice("After", "after")
                .addChoice("Before", "before")

        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();

        int playlistId = event.getOption("playlist-name").getAsInt();
        int selected = event.getOption("playlist-song").getAsInt();
        int place = event.getOption("playlist-order").getAsInt();
        String order = event.getOption("order").getAsString();

        QueryResult playlist = DatabaseHandler.getPlaylistTracks(playlistId, null, null);
        
        HashMap<Integer, Integer> song = new HashMap<>();
        for (ResultRow row : playlist) 
            song.put(row.getAsInt("id"), row.getAsInt("order"));
        
        List<String> tracks = playlist.arrayColumn("id");

        selected = song.get(selected);
        place = song.get(place);

        switch (order) {
            case "swap":
                tracks.set(selected, tracks.set(place, tracks.get(selected)));
                break;
            case "move":
                tracks.add(place, tracks.remove(selected));
                break;
            case "after":
                tracks.add(place + 1, tracks.remove(selected));
                break;
            case "before":
                tracks.add(place - 1, tracks.remove(selected));
                break;
            default:
                break;
        }

        int result = DatabaseHandler.updatePlaylistOrder(playlistId, event.getUser().getId(), tracks);

        if(result == 0) event.getHook().editOriginal("Playlist not found.").queue();
        else if(result == -1) event.getHook().editOriginal("You are not the creator of this playlist.").queue();
        else if(result == -2) event.getHook().editOriginal("An error occurred.").queue();
        else event.getHook().editOriginal("Order changed correctly").queue();
    }
}
