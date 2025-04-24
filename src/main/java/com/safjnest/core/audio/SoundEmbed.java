package com.safjnest.core.audio;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;

public class SoundEmbed {

    public static List<LayoutComponent> getSoundButton(String sound) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

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

    public static List<LayoutComponent> getTagButton(String sound, String tag) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        QueryRecord tagData = DatabaseHandler.getTag(tag);

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
        eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.addField("Author", "```"
                + Bot.getJDA().getUserById(sound.getUserId()).getName()
                + "```", true);

        String formattedDuration = "";
        try {
            if (sound.getTrack() != null)
                formattedDuration = SafJNest.getFormattedDuration(sound.getTrack().getDuration());
            else if (sound.getExtension().equals("opus"))
                formattedDuration = SafJNest
                        .getFormattedDuration((Math.round(SoundCache.getOpusDuration(sound.getPath()))) * 1000);
            else
                formattedDuration = SafJNest
                        .getFormattedDuration((Math.round(SoundCache.getMP3Duration(sound.getPath()))) * 1000);
        } catch (IOException e) {
            formattedDuration = "Error";
        }

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

    public static List<LayoutComponent> getSoundEmbedButtons(Sound sound) {
        int[] likes = sound.getLikesDislikes(false);
        Button like = Button.primary("soundplay-like-" + sound.getId(), String.valueOf(likes[0]))
                .withEmoji(CustomEmojiHandler.getRichEmoji("like"));
        Button dislike = Button.danger("soundplay-dislike-" + sound.getId(), String.valueOf(likes[1]))
                .withEmoji(CustomEmojiHandler.getRichEmoji("dislike"));
        Button replay = Button.success("soundplay-replay-" + sound.getId(), " ")
                .withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        buttonRows.add(ActionRow.of(
                like,
                dislike,
                replay));

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

    public static List<Sound> getSoundboardSounds(String soundboardID) {
        QueryCollection sounds = DatabaseHandler.getSoundsFromSoundBoard(soundboardID);
        return SoundCache.getSoundsByIds(sounds.arrayColumn("sound_id").toArray(new String[0]));
    }

    public static boolean isValidThumbnail(Attachment thumbnail) {
        if (thumbnail == null)
            return false;
        if (thumbnail.getSize() > 1024 * 1024)
            return false;
        if (List.of("png", "jpg", "jpeg", "gif").stream().noneMatch(thumbnail.getFileExtension()::equals))
            return false;
        return true;
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String soundboardID) {
        QueryRecord data = DatabaseHandler.getSoundboardByID(soundboardID);
        if (data.emptyValues())
            return event.deferReply().setContent("Soundboard not found").setEphemeral(true);

        String name = data.get("name");
        Blob thumbnailBlob = data.getAsBlob("thumbnail");
        InputStream thumbnailStream = null;

        if (thumbnailBlob != null) {
            try {
                thumbnailStream = thumbnailBlob.getBinaryStream();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        List<Sound> soundList = getSoundboardSounds(soundboardID);
        return composeSoundboard(event, name, thumbnailStream, soundList);
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String name, List<Sound> sounds) {
        return composeSoundboard(event, name, null, sounds);
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String name, InputStream thumbnail,
            List<Sound> sounds) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName() + " requested:", "https://github.com/SafJNest",
                event.getUser().getAvatarUrl());
        eb.setTitle("**" + name + "**");

        if (thumbnail != null)
            eb.setThumbnail("attachment://thumbnail.png");
        else
            eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.setDescription("Press a button to play a sound");
        eb.setColor(Bot.getColor());
        eb.setFooter(sounds.size() + " sounds");

        List<LayoutComponent> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();
        for (int i = 0; i < sounds.size(); i++) {
            row.add(Button.primary("soundboard-" + sounds.get(i).getId() + "." + sounds.get(i).getExtension(),
                    sounds.get(i).getName()));
            if (row.size() == 5 || i == sounds.size() - 1) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
        }

        if (thumbnail != null)
            return event.deferReply().addEmbeds(eb.build()).addFiles(FileUpload.fromData(thumbnail, "thumbnail.png"))
                    .setComponents(rows);
        return event.deferReply().addEmbeds(eb.build()).setComponents(rows);
    }

    public static void composeSoundboard(CommandEvent event, String soundboardID) {
        QueryRecord data = DatabaseHandler.getSoundboardByID(soundboardID);

        String name = data.get("name");
        Blob thumbnailBlob = data.getAsBlob("thumbnail");
        InputStream thumbnailStream = null;

        if (thumbnailBlob != null) {
            try {
                thumbnailStream = thumbnailBlob.getBinaryStream();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        List<Sound> sounds = getSoundboardSounds(soundboardID);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getAuthor().getName() + " requested:", "https://github.com/SafJNest",
                event.getAuthor().getAvatarUrl());
        eb.setTitle("**" + name + "**");

        if (thumbnailStream != null)
            eb.setThumbnail("attachment://thumbnail.png");
        else
            eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.setDescription("Press a button to play a sound");
        eb.setColor(Bot.getColor());
        eb.setFooter(sounds.size() + " sounds");

        List<LayoutComponent> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();
        for (int i = 0; i < sounds.size(); i++) {
            row.add(Button.primary("soundboard-" + sounds.get(i).getId() + "." + sounds.get(i).getExtension(),
                    sounds.get(i).getName()));
            if (row.size() == 5 || i == sounds.size() - 1) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
        }

        if (thumbnailStream != null)
            event.getChannel().sendMessageEmbeds(eb.build())
                    .addFiles(FileUpload.fromData(thumbnailStream, "thumbnail.png")).setComponents(rows).queue();
        else
            event.getChannel().sendMessageEmbeds(eb.build()).setComponents(rows).queue();
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

    public static List<LayoutComponent> getGreetButton(String userId, String GuildId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

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

    public static List<LayoutComponent> getGreetSoundButton(String userId, String type, String soundId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

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
