package com.safjnest.util.spotify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;

public class SpotifyMessage {
  

  public static Container getButtonComponents(String type) throws IOException {
    return getButtonComponents(type, 0);
  }


  public static Container getButtonComponents(String type, int index) throws IOException {
    List<SpotifyTrackStreaming> streamings = SpotifyHandler.readStreamsInfoFromZip("rsc" + File.separator + "my_spotify_data.zip");
    
    Button left = Button.primary("spotify-left", " ")
        .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));

    Button center = Button.primary("spotify-center-" + index, "Page: " + (index + 1))
        .withStyle(ButtonStyle.SUCCESS)
        .asDisabled();

    Button right = Button.primary("spotify-right", " ")
        .withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));



    Button albumsButton = Button.primary("spotify-type-albums", "Albums")
        .withEmoji(CustomEmojiHandler.getRichEmoji("playlist"));

    Button tracksButton = Button.primary("spotify-type-tracks", "Tracks")
        .withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));

    Button authorsButton = Button.primary("spotify-type-authors", "Authors")
        .withEmoji(CustomEmojiHandler.getRichEmoji("microphone"));

    int size = 0;
    switch (type) {
        case "tracks":
            tracksButton = tracksButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
            size = SpotifyHandler.getSortedTracks(streamings).size();
            break;
        case "albums":
            albumsButton = albumsButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
            size = SpotifyHandler.getSortedAlbums(streamings).size();
            break;
        case "authors":
            authorsButton = authorsButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
            break;

        default:
            break;
    }


    if (index == 0) left = left.asDisabled().withStyle(ButtonStyle.DANGER);
    if (index >= (size / 5)) right = right.asDisabled().withStyle(ButtonStyle.DANGER);

    List<ContainerChildComponent> buttons = new ArrayList<>();
    buttons.add(ActionRow.of(left, center, right));
    buttons.add(Separator.createDivider(Separator.Spacing.SMALL));
    buttons.add(ActionRow.of(albumsButton,tracksButton,authorsButton));

    return Container.of(buttons)
        .withAccentColor(Bot.getColor());
  }



  public static Container getMainContent(String type, int index) throws IOException {
    List<SpotifyTrackStreaming> streamings = SpotifyHandler.readStreamsInfoFromZip("rsc" + File.separator + "my_spotify_data.zip");

    switch (type) {
      case "tracks":
        return getTopTracks(index, streamings);
      case "albums":
        return getTopAlbums(index, streamings);
      case "authors":
      default:
        return Container.of(TextDisplay.of("This feature is not implemented yet.")).withAccentColor(Bot.getColor());
    }
  }


  private static Container getTopTracks(int index, List<SpotifyTrackStreaming> streamings) {
    List<Map.Entry<SpotifyTrack, Long>> sortedEntries = SpotifyHandler.getSortedTracks(streamings);
    List<Map.Entry<SpotifyTrack, Long>> limitedEntries = sortedEntries.stream()
        .skip(index)
        .limit(5)
        .toList();

    List<ContainerChildComponent> children = new ArrayList<>();
    for (int i = 0; i < limitedEntries.size(); i++) {
      children.add(getTrackRow(i, limitedEntries.get(i).getKey(), limitedEntries.get(i).getValue()));
    }

    return Container.of(children).withAccentColor(Bot.getColor());
  }

    private static Section getTrackRow(int position, SpotifyTrack track, long playCount) {
        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            SafJNest.intToEmojiDigits(position + 1),
            playCount > 1 ? "Played " + playCount + " times" : "",
            track.getName(),
            track.getArtist()
        );

        return Section.of(
            Thumbnail.fromUrl(SpotifyHandler.getTrackImage(track.getURI())),
            TextDisplay.of(rowDescription)
        );
    }


    private static Container getTopAlbums(int index, List<SpotifyTrackStreaming> streamings) {
        List<Map.Entry<SpotifyAlbum, Long>> sortedEntries = SpotifyHandler.getSortedAlbums(streamings);
        List<Map.Entry<SpotifyAlbum, Long>> limitedEntries = sortedEntries.stream()
            .skip(index)
            .limit(5)
            .toList();

        List<ContainerChildComponent> children = new ArrayList<>();
        for (int i = 0; i < limitedEntries.size(); i++) {
            children.add(getAlbumRow(i, limitedEntries.get(i).getKey(), limitedEntries.get(i).getValue()));
        }

        return Container.of(children).withAccentColor(Bot.getColor());
    }

    private static Section getAlbumRow(int position, SpotifyAlbum album, long playCount) {
        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            SafJNest.intToEmojiDigits(position + 1),
            playCount > 1 ? "Played " + playCount + " times" : "",
            album.getName(),
            album.getArtist()
            
        );

        return Section.of(
            Thumbnail.fromUrl(SpotifyHandler.getTrackImage(album.getTracks().get(0).getURI())),
            TextDisplay.of(rowDescription)
        );
    }



}
