package com.safjnest.commands.audio.playlist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistDelete extends SlashCommand {
    
    public PlaylistDelete(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Name of the playlist to delete", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().setEphemeral(true).queue();

        int playlistId = event.getOption("name").getAsInt();//ti da direttamente l'id della playlist uwu gna gna

        BotDB.deletePlaylist(playlistId, event.getUser().getId());

        event.getHook().editOriginal("Playlist deleted successfully.").queue();
    }
}
