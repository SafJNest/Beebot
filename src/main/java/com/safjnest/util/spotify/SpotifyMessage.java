package com.safjnest.util.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyArtist;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.sql.SpotifyDBHandler;
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
  

  public static Container getButtonComponents(String type, String userId) throws IOException {
    return getButtonComponents(type, userId, 0);
  }


  public static Container getButtonComponents(String type, String userId, int index) throws IOException {    
    Button left = Button.primary("spotify-left", " ")
        .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));

    Button center = Button.primary("spotify-center-" + index + "-" + userId, "Page " + (index / 5 + 1))
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
            //size = SpotifyHandler.getSortedTracks(streamings).size();
            break;
        case "albums":
            albumsButton = albumsButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
            //size = SpotifyHandler.getSortedAlbums(streamings).size();
            break;
        case "authors":
            authorsButton = authorsButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
            break;

        default:
            break;
    }

    if (index == 0) left = left.asDisabled().withStyle(ButtonStyle.DANGER);
    //if (index >= (size / 5)) right = right.asDisabled().withStyle(ButtonStyle.DANGER);

    List<ContainerChildComponent> buttons = new ArrayList<>();
    buttons.add(ActionRow.of(left, center, right));
    buttons.add(Separator.createDivider(Separator.Spacing.SMALL));
    buttons.add(ActionRow.of(albumsButton,tracksButton,authorsButton));

    return Container.of(buttons)
        .withAccentColor(Bot.getColor());
  }



  public static List<Container> build(String userId, String type, int index) {
    List<?> values = new ArrayList<>();
    switch (type) {
        case "albums":
            values = SpotifyDBHandler.getTopAlbums(userId, 5, index);
            break;
        case "tracks":
            values = SpotifyDBHandler.getTopTracks(userId, 5, index); 
            break;
        case "authors":
            values = SpotifyDBHandler.getTopArtists(userId, 5, index);
            break;
    
        default:
            break;
    }
    Container body = null, buttons = null;
    try {
        body = getMainContent(userId, type, index, values);
        buttons = getButtonComponents(type, userId, index);
    } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }

    return List.of(body, buttons);
  }



    @SuppressWarnings("unchecked")
    public static Container getMainContent(String userId, String type, int index, List<?> values) throws IOException {
        switch (type) {
            case "tracks":
                return getTopTracks(userId, index, (List<SpotifyTrack>) values);
            case "albums":
                return getTopAlbums(userId, index, (List<SpotifyAlbum>) values);
            case "authors":
                return getTopArtists(userId, index, (List<SpotifyArtist>) values);
            default:
                return Container.of(TextDisplay.of("This feature is not implemented yet."))
                    .withAccentColor(Bot.getColor());
        }
    }

    private static Container getTopTracks(String userId, int index, List<SpotifyTrack> tracks) {
        List<ContainerChildComponent> children = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            children.add(getTrackRow(i + index, tracks.get(i)));
        }

        return Container.of(children).withAccentColor(Bot.getColor());
    }

    private static Section getTrackRow(int position, SpotifyTrack track) {
        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            SafJNest.intToEmojiDigits(position + 1),
            track.getPlayCount() > 1 ? "Played " + track.getPlayCount() + " times" : "",
            track.getName(),
            track.getArtist()
        );

        return Section.of(
            Thumbnail.fromUrl(SpotifyHandler.getTrackImage(track.getURI())),
            TextDisplay.of(rowDescription)
        );
    }


    private static Container getTopAlbums(String userId, int index, List<SpotifyAlbum> albums) {
        List<ContainerChildComponent> children = new ArrayList<>();
        for (int i = 0; i < albums.size(); i++) {
            children.add(getAlbumRow(i + index, albums.get(i)));
        }

        return Container.of(children).withAccentColor(Bot.getColor());
    }

    private static Section getAlbumRow(int position, SpotifyAlbum album) {
        String rowDescription = String.format(
            "%s - %s\n**%s**\n-# %s",
            SafJNest.intToEmojiDigits(position + 1),
            album.getPlayCount() > 1 ? "Played " + album.getPlayCount() + " times" : "",
            album.getName(),
            album.getArtist()
            
        );

        return Section.of(
            Thumbnail.fromUrl(SpotifyHandler.getTrackImage(album.getTracks().get(0).getURI())),
            TextDisplay.of(rowDescription)
        );
    }

    private static Container getTopArtists(String userId, int index, List<SpotifyArtist> artists) {

        List<ContainerChildComponent> children = new ArrayList<>();
        for (int i = 0; i < artists.size(); i++) {
            children.add(getArtistRow(i + index, artists.get(i)));
        }

        return Container.of(children).withAccentColor(Bot.getColor());
    }

    private static Section getArtistRow(int position, SpotifyArtist artist) {
        String rowDescription = String.format(
            "%s - %s\n**%s**",
            SafJNest.intToEmojiDigits(position + 1),
            artist.getPlayCount() > 1 ? "Played " + artist.getPlayCount() + " times" : "",
            artist.getName()
        );

        return Section.of(
            Thumbnail.fromUrl(SpotifyHandler.getArtistImageFromTrack(artist.getRandomTrackUri())),
            TextDisplay.of(rowDescription)
        );
    }

    
}
