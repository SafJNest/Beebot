package com.safjnest.bot.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.Bot;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.UserCache;
import com.safjnest.bot.model.customemoji.CustomEmojiHandler;
import com.safjnest.database.R2;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundboardData;
import com.safjnest.exception.Exception;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class SoundEmbed {

    public static List<MessageTopLevelComponent> getSoundButton(String sound) {
        java.util.List<MessageTopLevelComponent> buttonRows = new ArrayList<>();

        Sound soundData = SoundCache.getSoundById(sound);

        Button id = Button.primary("sound-id", "ID: " + sound).asDisabled();
        Button name = Button.secondary("sound-name", soundData.getName())
                .withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));
        Button isPrivate = Button.secondary("sound-private", " ").withEmoji(CustomEmojiHandler.getRichEmoji("lock"));

        if (soundData.isPublic()) {
            isPrivate = Button.success("sound-private", " ").withEmoji(CustomEmojiHandler.getRichEmoji("lock"));
        }

        Button delete = Button.danger("sound-delete", " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));
        Button download = Button.secondary("sound-download", " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("download"));

        buttonRows.add(ActionRow.of(
                id,
                name,
                isPrivate,
                download,
                delete));

        List<Tag> tags = soundData.getTags();
        List<Button> tagButtons = new ArrayList<>();
        int n = 1;
        for (Tag tag : tags) {
            Button tagButton = null;
            if (tag.isEmpty())
                tagButton = Button.secondary("sound-tag-empty-" + n, " ")
                        .withEmoji(CustomEmojiHandler.getRichEmoji("tag"));
            else
                tagButton = Button.primary("sound-tag-" + sound + "-" + tag.getId(), tag.getName())
                        .withEmoji(CustomEmojiHandler.getRichEmoji("tag"));

            tagButtons.add(tagButton);
            if (n % 5 == 0 || n == tags.size()) {
                buttonRows.add(ActionRow.of(tagButtons));
                tagButtons = new ArrayList<>();
            }
            n++;
        }

        // buttonRows.add(ActionRow.of(tagButtons));

        return buttonRows;
    }

    public static List<MessageTopLevelComponent> getTagButton(String sound, String tag) {
        java.util.List<MessageTopLevelComponent> buttonRows = new ArrayList<>();
        QueryRecord tagData = BotDB.getTag(tag);

        String name_tag = tagData.get("name") == null ? " " : tagData.get("name");

        Button back = Button.primary("tag-back-" + sound + "-" + tag, " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button tagName = Button.success("tag-name-" + sound + "-" + tag, name_tag)
                .withEmoji(CustomEmojiHandler.getRichEmoji("tag"));
        Button delete = Button.danger("tag-delete-" + sound + "-" + tag, " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(
                back,
                tagName,
                delete));

        return buttonRows;
    }

    public static EmbedBuilder getSoundEmbed(Sound sound, User author) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(author.getName(), "https://github.com/SafJNest", author.getAvatarUrl());
        eb.setTitle("Playing now:");
        eb.setDescription("```" + sound.getName() + " (ID: " + sound.getId() + ") "
                + ((sound.isPublic()) ? ":public:" : ":private:") + "```");
        eb.setColor(Bot.getColor());
        String thumbnail = SoundFiles.getInstance().imageUrl(sound.getId(), sound.getImageKey());
        eb.setThumbnail(thumbnail == null ? Bot.getJDA().getSelfUser().getAvatarUrl() : thumbnail);

        eb.addField("Author", "```"
                + Bot.getJDA().getUserById(sound.getUserId()).getName()
                + "```", true);

        Long durationMs = sound.getDurationMs();
        String formattedDuration = durationMs == null ? "Unknown" : SafJNest.getFormattedDuration(durationMs);

        eb.addField("Lenght", "```"
                + formattedDuration
                + "```", true);

        eb.addField("Guild", "```"
                + Bot.getJDA().getGuildById(sound.getGuildId()).getName()
                + "```", true);

        List<Tag> tags = sound.getTags();
        StringBuilder tagList = new StringBuilder();
        for (Tag tag : tags) {
            if (!tag.getName().isBlank())
                tagList.append(tag.getName()).append(", ");
        }

        if (!tagList.isEmpty() && tagList.length() > 2) {
            eb.addField("Tags", "```"
                    + tagList.toString().substring(0, tagList.length() - 2)
                    + "```", false);
        }

        eb.addField("Creation time",
                "<t:" + sound.getTimestampSecond() + ":f>" + " | <t:" + sound.getTimestampSecond() + ":R>",
                false);

        int[] plays = sound.getPlays(author.getId());
        eb.setFooter("Listened: " + plays[1] + (plays[1] == 1 ? " time" : " times") + " (" + sound.getGlobalPlays()
                + " total)");

        return eb;
    }

    public static List<MessageTopLevelComponent> getSoundEmbedButtons(Sound sound) {
        int[] likes = sound.getLikesDislikes(false);
        Button like = Button.primary("soundplay-like-" + sound.getId(), String.valueOf(likes[0]))
                .withEmoji(CustomEmojiHandler.getRichEmoji("like"));
        Button dislike = Button.danger("soundplay-dislike-" + sound.getId(), String.valueOf(likes[1]))
                .withEmoji(CustomEmojiHandler.getRichEmoji("dislike"));
        Button replay = Button.success("soundplay-replay-" + sound.getId(), " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        Button stop = Button.danger("soundplay-stop-" + sound.getId(), " ")
        .withEmoji(CustomEmojiHandler.getRichEmoji("stop"));

        java.util.List<MessageTopLevelComponent> buttonRows = new ArrayList<>();
        buttonRows.add(ActionRow.of(
                like,
                dislike,
                replay,
                stop));

        return buttonRows;
    }

    // ▄████████ ▄██████▄ ███ █▄ ███▄▄▄▄ ████████▄ ▀█████████▄ ▄██████▄ ▄████████
    // ▄████████ ████████▄
    // ███ ███ ███ ███ ███ ███ ███▀▀▀██▄ ███ ▀███ ███ ███ ███ ███ ███ ███ ███ ███
    // ███ ▀███
    // ███ █▀ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ███
    // ███
    // ███ ███ ███ ███ ███ ███ ███ ███ ███ ▄███▄▄▄██▀ ███ ███ ███ ███ ▄███▄▄▄▄██▀
    // ███ ███
    // ▀███████████ ███ ███ ███ ███ ███ ███ ███ ███ ▀▀███▀▀▀██▄ ███ ███ ▀███████████
    // ▀▀███▀▀▀▀▀ ███ ███
    // ███ ███ ███ ███ ███ ███ ███ ███ ███ ███ ██▄ ███ ███ ███ ███ ▀███████████ ███
    // ███
    // ▄█ ███ ███ ███ ███ ███ ███ ███ ███ ▄███ ███ ███ ███ ███ ███ ███ ███ ███ ███
    // ▄███
    // ▄████████▀ ▀██████▀ ████████▀ ▀█ █▀ ████████▀ ▄█████████▀ ▀██████▀ ███ █▀ ███
    // ███ ████████▀
    // ███ ███

    private static List<Sound> getSoundboardSounds(QueryRecord board, String viewer, Set<String> guilds) {
        List<String> ids = new ArrayList<>();
        SoundCache cache = SoundCache.getInstance();
        for (QueryRecord sound : cache.soundboardRows(board, viewer, guilds, true))
            ids.add(sound.get("id"));

        return cache.load(ids);
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String soundboardId) {
        String viewer = event.getUser().getId();
        String guildId = event.getGuild().getId();
        Set<String> guilds = Set.of(guildId);
        QueryRecord board;
        try {
            board = BotDB.get().visibleSoundboard(soundboardId, viewer, guilds);
        } catch (Exception failure) {
            return event.deferReply(true).setContent("Soundboard not found");
        }
        boolean personal = !board.getAsBoolean("public") && !guildId.equals(board.get("guild_id"));
        Set<String> contentScope = guildId.equals(board.get("guild_id")) ? guilds : Set.of();
        Thumbnail thumbnail = soundboardThumbnail(new SoundboardData(board.get("name"), board.get("thumbnail")),
            event.getJDA().getSelfUser().getAvatarUrl());
        List<Container> components = soundboardComponents(thumbnail, getSoundboardSounds(board, personal ? viewer : null, contentScope), soundboardId);

        return event.deferReply(personal).addComponents(components).useComponentsV2();
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, List<Sound> sounds) {
        Thumbnail thumbnail = Thumbnail.fromUrl(event.getJDA().getSelfUser().getAvatarUrl());

        boolean personal = false;
        for (Sound sound : sounds) {
            if (!sound.isPublic() && !event.getGuild().getId().equals(sound.getGuildId())) {
                personal = true;
                break;
            }
        }

        return event.deferReply(personal).addComponents(soundboardComponents(thumbnail, sounds, null)).useComponentsV2();
    }

    public static void showCreatedSoundboard(InteractionHook hook, String soundboardId, String guildId) {
        QueryRecord board = BotDB.get().visibleSoundboard(soundboardId, null, Set.of(guildId));
        Thumbnail thumbnail = soundboardThumbnail(new SoundboardData(board.get("name"), board.get("thumbnail")),
            hook.getJDA().getSelfUser().getAvatarUrl());
        List<Sound> sounds = getSoundboardSounds(board, null, Set.of(guildId));
        List<Container> components = soundboardComponents(thumbnail, sounds, soundboardId);

        hook.editOriginalComponents(components).useComponentsV2().queue();
    }

    private static Thumbnail soundboardThumbnail(SoundboardData board, String avatarUrl) {
        String url = thumbnailUrl(board);
        if (url != null)
            return Thumbnail.fromUrl(url);

        return Thumbnail.fromUrl(avatarUrl);
    }

    private static List<Container> soundboardComponents(Thumbnail thumbnail, List<Sound> sounds, String boardId) {
        List<Container> containers = new ArrayList<>();
        List<ContainerChildComponent> children = new ArrayList<>();

        children.add(Section.of(
            thumbnail,
            TextDisplay.of("Press a button to play a sound.\n" + sounds.size() + " / 25 sounds.")
        ));
        containers.add(Container.of(children).withAccentColor(Bot.getColor()));
        children.clear();

        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < sounds.size(); i++) {
            buttons.add(soundboardButton(sounds.get(i), boardId));
            if (buttons.size() == 5 || i == sounds.size() - 1) {
                children.add(ActionRow.of(buttons));
                buttons = new ArrayList<>();
            }
        }
        if (children.isEmpty())
            return containers;

        containers.add(Container.of(children).withAccentColor(Bot.getColor()));

        Button random = Button.primary("soundboard-random", " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("shuffle"));
        Button stop = Button.danger("soundboard-stop", " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("stop"));
        containers.add(Container.of(ActionRow.of(random, stop)).withAccentColor(Bot.getColor()));
        return containers;
    }

    public static void composeSoundboard(CommandEvent event, String soundboardId) {
        String guildId = event.getGuild().getId();
        Set<String> guilds = Set.of(guildId);
        QueryRecord data;
        try {
            data = BotDB.get().visibleSoundboard(soundboardId, event.getAuthor().getId(), guilds);
        } catch (Exception failure) {
            event.reply("Soundboard not found");
            return;
        }
        if (!data.getAsBoolean("public") && !guildId.equals(data.get("guild_id"))) {
            event.reply("Use /soundboard play to open a personal soundboard.");
            return;
        }

        List<Sound> sounds = getSoundboardSounds(data, null, guilds);
        SoundboardData board = new SoundboardData(data.get("name"), data.get("thumbnail"));
        String url = thumbnailUrl(board);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getAuthor().getName() + " requested:", "https://github.com/SafJNest",
                event.getAuthor().getAvatarUrl());
        eb.setTitle("**" + board.name() + "**");

        eb.setThumbnail(url != null ? url : Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.setDescription("Press a button to play a sound");
        eb.setColor(Bot.getColor());
        eb.setFooter(sounds.size() + " sounds");

        List<MessageTopLevelComponent> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();
        for (int i = 0; i < sounds.size(); i++) {
            row.add(soundboardButton(sounds.get(i), soundboardId));
            if (row.size() == 5 || i == sounds.size() - 1) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
        }

        event.getChannel().sendMessageEmbeds(eb.build()).setComponents(rows).queue();
    }

    private static Button soundboardButton(Sound sound, String boardId) {
        String id = "soundboard-" + sound.getId() + "." + sound.getExtension();
        if (boardId != null)
            id += "-" + boardId;

        return Button.primary(id, sound.getName());
    }

    private static String thumbnailUrl(SoundboardData board) {
        return board.thumbnailKey() == null ? null : R2.getInstance().url(board.thumbnailKey());
    }

    public static EmbedBuilder getGreetViewEmbed(String userId, String guildId) {
        String globalGreetId = UserCache.getUser(userId).getGlobalGreet();
        String guildGreetId = UserCache.getUser(userId).getGuildGreet(guildId);

        Sound globalGreet = (globalGreetId != null && !globalGreetId.isBlank())
                ? SoundCache.getSoundById(globalGreetId)
                : null;
        Sound guildGreet = (guildGreetId != null && !guildGreetId.isBlank()) ? SoundCache.getSoundById(guildGreetId)
                : null;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Your greet sounds");
        eb.setThumbnail(Bot.getJDA().getUserById(userId).getAvatarUrl());
        eb.setColor(Bot.getColor());
        eb.setDescription("A greet sound is a sound that will be played when you join a voice channel!.\n" +
                "You can set a global greet sound that will be played in every server, or a specific greet sound for each server.\n"
                +
                "Click on the buttons to set, change, listen or delete your greet");

        eb.addField("Global greet", "```" + (globalGreet != null ? globalGreet.getName() : "Not set") + "```", true);
        eb.addField("Guild greet", "```" + (guildGreet != null ? guildGreet.getName() : "Not set") + "```", true);

        return eb;
    }

    public static List<MessageTopLevelComponent> getGreetButton(String userId, String GuildId) {
        java.util.List<MessageTopLevelComponent> buttonRows = new ArrayList<>();

        String globalGreetId = UserCache.getUser(userId).getGlobalGreet();
        String guildGreetId = UserCache.getUser(userId).getGuildGreet(GuildId);

        Sound globalGreet = (globalGreetId != null && !globalGreetId.isBlank())
                ? SoundCache.getSoundById(globalGreetId)
                : null;
        Sound guildGreet = (guildGreetId != null && !guildGreetId.isBlank()) ? SoundCache.getSoundById(guildGreetId)
                : null;

        Button globalGreetButton, guildGreetButton,
                userButton = Button.primary("greet-user-" + userId, UserCache.getUser(userId).getName()).asDisabled()
                        .withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        if (globalGreet != null)
            globalGreetButton = Button.primary("greet-global", globalGreet.getName());
        else
            globalGreetButton = Button.secondary("greet-set-global", "Set global greet");

        if (guildGreet != null)
            guildGreetButton = Button.primary("greet-guild", guildGreet.getName());
        else
            guildGreetButton = Button.secondary("greet-set-guild", "Set guild greet");

        buttonRows.add(ActionRow.of(
                userButton,
                globalGreetButton,
                guildGreetButton));

        return buttonRows;
    }

    public static List<MessageTopLevelComponent> getGreetSoundButton(String userId, String type, String soundId) {
        java.util.List<MessageTopLevelComponent> buttonRows = new ArrayList<>();

        Sound sound = SoundCache.getSoundById(soundId);

        Button back = Button.primary("greet-back-" + userId, " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button change = Button.success("greet-set-" + type, sound.getName())
                .withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));
        Button play = Button.primary("soundboard-" + sound.getId() + "." + sound.getExtension(), " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("audio"));
        Button delete = Button.danger("greet-delete-" + type, " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(
                back,
                play,
                change,
                delete));

        return buttonRows;
    }
}
