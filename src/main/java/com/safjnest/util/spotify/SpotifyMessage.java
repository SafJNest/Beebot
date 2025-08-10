package com.safjnest.util.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyArtist;
import com.safjnest.model.spotify.SpotifyTrack;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;

public class SpotifyMessage {
    public static final int ITEM_LIMIT = 5;

    public static void send(InteractionHook hook, String userId, SpotifyMessageType type, int index, SpotifyTimeRange timeRange) {
        List<Container> components = null;
        try {
            components = SpotifyMessage.build(userId, type, index, timeRange);
        } catch (SpotifyException e) {
            System.out.println(e.getMessage());
            switch (e.getType()) {
                case NOT_LINKED -> hook.sendMessage("You need to link your Spotify account first. Use `/spotify link` to do so.").setEphemeral(true).queue();
                case HISTORY_MISSING -> {
                    hook.sendMessage("You need to upload your extended Spotify history first. Use `/spotify upload` to do so. You can obtain your extended history [here](https://www.spotify.com/us/account/privacy/)").setEphemeral(true).queue();
                }
                case NOT_SUPPORTED -> {
                    hook.sendMessage("Due to spotify limitations top albums is only supported with extended history.").setEphemeral(true).queue();

                    List<Object> oldTimeMsgInfo = getMsgInfo(hook.retrieveOriginal().complete());
                    String oldUserId = (String) oldTimeMsgInfo.get(0);
                    SpotifyMessageType oldType = (SpotifyMessageType) oldTimeMsgInfo.get(1);
                    int oldIndex = (int) oldTimeMsgInfo.get(2);
                    SpotifyTimeRange oldRange = (SpotifyTimeRange) oldTimeMsgInfo.get(3);

                    try {
                        List<Container> fallbackComponents = SpotifyMessage.build(oldUserId, oldType, oldIndex, oldRange);
                        hook.editOriginalComponents(fallbackComponents)
                            .useComponentsV2()
                            .queue();
                    } catch (SpotifyException ex) {
                        ex.printStackTrace();
                    }
                }
                case API_ERROR -> hook.sendMessage("An error occurred while fetching data from Spotify. Please try again later or use `/bug` to write a report with code " + SpotifyException.ErrorType.API_ERROR + ".").setEphemeral(true).queue();
                case ERROR_PARSING -> hook.sendMessage("An error occurred. Please try again later or use `/bug` to write a report with code " + SpotifyException.ErrorType.ERROR_PARSING + ".").setEphemeral(true).queue();
                case INVALID_TIME_RANGE -> hook.sendMessage("Invalid time range selected. Please choose a valid time range.").setEphemeral(true).queue();
                case NO_AUTH -> hook.sendMessage("The authorization to this app could've been revoked, try to link spotify again using `/spotify link`.").setEphemeral(true).queue();
            }
            return;
        }

        hook.editOriginalComponents(components)
            .useComponentsV2()
            .queue();
    }

    public static List<Object> getMsgInfo(Message message) {
        Container buttonContainer = message.getComponents().get(1).asContainer();

        String userId = null;
        SpotifyMessageType type = null;
        int currentIndex = -1;
        SpotifyTimeRange timeRange = null;

        for (Component component : buttonContainer.getComponents()) {
            if (component instanceof ActionRow actionRow) {
                for (Component child : actionRow.getComponents()) {
                    if (child instanceof Button button) {
                        if (button.getCustomId().startsWith("spotify-type-") && button.getStyle() == ButtonStyle.SUCCESS) {
                            type = SpotifyMessageType.valueOf((button.getCustomId().split("-")[2]).toUpperCase());
                        } else if (button.getCustomId().startsWith("spotify-center-")) {
                            currentIndex = Integer.parseInt(button.getCustomId().split("-")[2]);
                            userId = button.getCustomId().split("-")[3];
                        }
                    }
                    else if(child instanceof StringSelectMenu selectMenu) {
                        if (selectMenu.getCustomId().startsWith("spotify-time_range-")) {
                            timeRange = SpotifyTimeRange.fromApiLabel(selectMenu.getCustomId().split("-")[2]);
                        }
                    }
                }
            }
        }

        return List.of(userId, type, currentIndex, timeRange);
    }



    public static Container getButtonComponents(SpotifyMessageType type, String userId, SpotifyTimeRange timeRange) throws IOException {
        return getButtonComponents(type, userId, 0, timeRange);
    }


    public static Container getButtonComponents(SpotifyMessageType type, String userId, int index, SpotifyTimeRange timeRange) throws IOException {    
        Button left = Button.primary("spotify-left", " ")
            .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));

        Button center = Button.primary("spotify-center-" + index + "-" + userId, "Page " + (index / ITEM_LIMIT + 1))
            .withStyle(ButtonStyle.SUCCESS)
            .asDisabled();

        Button right = Button.primary("spotify-right", " ")
            .withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));


        Button albumsButton = Button.primary(SpotifyMessageType.ALBUMS.toButtonId(), SpotifyMessageType.ALBUMS.getLabel())
            .withEmoji(CustomEmojiHandler.getRichEmoji("playlist"));

        Button tracksButton = Button.primary(SpotifyMessageType.TRACKS.toButtonId(), SpotifyMessageType.TRACKS.getLabel())
            .withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));

        Button authorsButton = Button.primary(SpotifyMessageType.ARTISTS.toButtonId(), SpotifyMessageType.ARTISTS.getLabel())
            .withEmoji(CustomEmojiHandler.getRichEmoji("microphone"));


        StringSelectMenu timeRangeMenu = StringSelectMenu.create("spotify-time_range-" + timeRange.getLabel())
            .setPlaceholder(timeRange.getDisplayLabel())
            .addOptions(SpotifyTimeRange.SHORT_TERM.toSelectOption(),
                        SpotifyTimeRange.MEDIUM_TERM.toSelectOption(),
                        SpotifyTimeRange.LONG_TERM.toSelectOption(),
                        SpotifyTimeRange.FULL_TERM.toSelectOption())
            .build();

        switch (type) {
            case TRACKS:
                tracksButton = tracksButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
                //size = SpotifyHandler.getSortedTracks(streamings).size();
                break;
            case ALBUMS:
                albumsButton = albumsButton.asDisabled().withStyle(ButtonStyle.SUCCESS);
                //size = SpotifyHandler.getSortedAlbums(streamings).size();
                break;
            case ARTISTS:
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
        buttons.add(ActionRow.of(timeRangeMenu));

        return Container.of(buttons)
            .withAccentColor(Bot.getColor());
    }


    public static List<Container> build(String userId, SpotifyMessageType type, int index, SpotifyTimeRange timeRange) {
        List<?> items = SpotifyHandler.getTopItems(type, userId, ITEM_LIMIT, index, timeRange);
        Container body = null, buttons = null;
        try {
            body = getMainContent(userId, type, index, items);
            buttons = getButtonComponents(type, userId, index, timeRange);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return List.of(body, buttons);
    }

    

    @SuppressWarnings("unchecked")
    public static Container getMainContent(String userId, SpotifyMessageType type, int index, List<?> items) throws IOException {
        switch (type) {
            case TRACKS:
                return getSpotifyContainer(index, (List<SpotifyTrack>) items, track -> 
                    buildRow(index + items.indexOf(track), track.getName(), track.getArtist(), 
                            track.getPlayCount(), track.getImageUrl())
                );

            case ALBUMS:
                return getSpotifyContainer(index, (List<SpotifyAlbum>) items, album ->
                    buildRow(index + items.indexOf(album), album.getName(), album.getArtist(),
                            album.getPlayCount(), album.getImageUrl())
                );
            case ARTISTS:
                return getSpotifyContainer(index, (List<SpotifyArtist>) items, artist ->
                    buildRow(index + items.indexOf(artist), artist.getName(), null,
                            artist.getPlayCount(), artist.getImageUrl())
                );
            default:
                return Container.of(TextDisplay.of("This feature is not implemented yet."))
                    .withAccentColor(Bot.getColor());
        }
    }


    private static <T> Container getSpotifyContainer(int startIndex, List<T> items, Function<T, Section> rowBuilder) {
        List<ContainerChildComponent> children = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            children.add(rowBuilder.apply(items.get(i)));
        }
        return Container.of(children).withAccentColor(Bot.getColor());
    }

    private static Section buildRow(int position, String name, String artist, int playCount, String imageUrl) {
        String rowDescription = String.format(
            artist != null && !artist.isEmpty()
                ? "%s %s\n**%s**\n-# %s"
                : "%s %s\n**%s**",
            SafJNest.intToEmojiDigits(position + 1),
            playCount > 0 ? "- Played " + playCount + " times" : "",
            name,
            artist
        );

        return Section.of(
            Thumbnail.fromUrl(imageUrl),
            TextDisplay.of(rowDescription)
        );
    }
}
