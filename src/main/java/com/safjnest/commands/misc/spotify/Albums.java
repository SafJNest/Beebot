package com.safjnest.commands.misc.spotify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.safjnest.util.spotify.Spotify;

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

public class Albums extends SlashCommand{
    private static final String spotifyPath = "rsc" + File.separator + "my_spotify_data.zip";

    public Albums() {
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
            List<SpotifyTrackStreaming> streamings = Spotify.readStreamsInfoFromZip(spotifyPath);

            List<Map.Entry<SpotifyAlbum, Long>> sortedEntries = Spotify.getSortedAlbums(streamings);

            List<Map.Entry<SpotifyAlbum, Long>> limitedEntries = sortedEntries.stream()
                .limit(5)
                .toList();
            
            List<Section> topAlbums = IntStream.range(0, limitedEntries.size())
                .mapToObj(i -> getAlbumRow(i, limitedEntries.get(i).getKey(), limitedEntries.get(i).getValue()))
                .toList();

            List<ContainerChildComponent> children = new ArrayList<>();
            children.add(TextDisplay.of("## Top Spotify Albums"));
            children.addAll(topAlbums);
            children.add(
                ActionRow.of(
                    Button.primary("spotify-tracks-back", " ")
                    .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow")),
                    Button.primary("spotify-tracks-center", "Page: 1")
                        .withStyle(ButtonStyle.SUCCESS)
                        .asDisabled(),
                    Button.primary("spotify-tracks-next", " ")
                    .withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"))
                )
            );

            event.replyComponents(Container.of(children))
                .useComponentsV2()
                .queue();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Section getAlbumRow(int position, SpotifyAlbum album, long playCount) {
        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            SafJNest.intToEmojiDigits(position + 1),
            playCount > 1 ? "Played " + playCount + " times" : "",
            album.getName(),
            album.getArtist()
            
        );

        return Section.of(
            Thumbnail.fromUrl(Spotify.getTrackImage(album.getTracks().get(0).getURI())),
            TextDisplay.of(rowDescription)
        );
    }
}
