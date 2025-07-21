package com.safjnest.commands.misc.spotify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jdom2.Text;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.safjnest.util.spotify.Spotify;
import com.safjnest.util.spotify.SpotifyMessage;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.components.mediagallery.MediaGalleryItemImpl;

public class Tracks extends SlashCommand{

    private static final String spotifyPath = "rsc" + File.separator + "my_spotify_data.zip";

    public Tracks() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            event.replyComponents(List.of(SpotifyMessage.getMainContent("tracks", 0), SpotifyMessage.getButtonComponents("tracks")))
                .useComponentsV2()
                .queue();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
