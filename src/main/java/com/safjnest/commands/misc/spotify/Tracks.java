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
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
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
            List<SpotifyTrackStreaming> streamings = Spotify.readStreamsInfoFromZip(spotifyPath);

            Map<SpotifyTrack, Long> tracks = streamings.stream()
                .filter(streaming -> streaming.getMsPlayed() >= 30000)
                .collect(Collectors.groupingBy(
                    streaming -> streaming.getTrack(),
                    Collectors.counting()
                ));

            List<Map.Entry<SpotifyTrack, Long>> sortedEntries = tracks.entrySet().stream()
                .sorted(Map.Entry.<SpotifyTrack, Long>comparingByValue().reversed())
                .limit(5)
                .toList();
            
            List<Section> topTracks = IntStream.range(0, sortedEntries.size())
                .mapToObj(i -> getTrackRow(i, sortedEntries.get(i).getKey(), sortedEntries.get(i).getValue()))
                .toList();

            List<ContainerChildComponent> children = new ArrayList<>();
            children.addAll(topTracks);
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

    private Section getTrackRow(int position, SpotifyTrack track, long playCount) {

        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            intToEmojiDigits(position + 1),
            playCount > 1 ? "Played " + playCount + " times" : "",
            track.getName(),
            track.getArtist()
        );

        return Section.of(
            Thumbnail.fromUrl(Spotify.getTrackImage(track.getURI())),
            TextDisplay.of(rowDescription)
        );

    }

    public static String intToEmojiDigits(int number) {
    String[] digitsToWords = {
        "zero", "one", "two", "three", "four",
        "five", "six", "seven", "eight", "nine"
    };

    StringBuilder result = new StringBuilder();
    String numStr = String.valueOf(number);

    for (char c : numStr.toCharArray()) {
        if (Character.isDigit(c)) {
            int digit = Character.getNumericValue(c);
            result.append(":").append(digitsToWords[digit]).append(":");
        } else {
            throw new IllegalArgumentException("Input must be a non-negative integer");
        }
    }

    return result.toString();
}
}
