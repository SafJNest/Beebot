package com.safjnest.core.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;//everybody was mocking you?
import java.util.ArrayList;
import java.util.List;

import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusStatistics;

import com.safjnest.core.Bot;
import com.safjnest.model.Sound;
import com.safjnest.model.Sound.Tag;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Contains the methods to manage the soundboard.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.1
 */
public class SoundBoard {

    public static OpusFile getOpus(String path) throws IOException{
        File initialFile = new File(path);
        InputStream targetStream = new FileInputStream(initialFile);
        OggFile ogg = new OggFile(targetStream);
        return new OpusFile(ogg);
    }

    public static double getOpusDuration(OpusFile opus) throws IOException {
        OpusStatistics stats = null;
        stats = new OpusStatistics(opus);
        stats.calculate();
        return stats.getDurationSeconds();
    }

    public static double getOpusDuration(String path) throws IOException {
        OpusFile opus = getOpus(path);
        return getOpusDuration(opus);
    }

    public static Sound getSoundByString(String regex, Guild guild, User user) {
        QueryResult sounds = regex.matches("[0123456789]*") 
            ? DatabaseHandler.getSoundsById(regex, guild.getId(), user.getId()) 
            : DatabaseHandler.getSoundsByName(regex, guild.getId(), user.getId());
    
        if(sounds.isEmpty()) {
            return null;
        }
    
        ResultRow toPlay = null;
        for(ResultRow sound : sounds) {
            if(sound.get("guild_id").equals(guild.getId())) {
                toPlay = sound;
                break;
            }
        }
    
        if(toPlay == null)
            toPlay = sounds.get((int)(Math.random() * sounds.size()));
    
        
        return getSoundById(toPlay.get("id"));

    }

    public static Sound getSoundById(String sound_id) {
        ResultRow soundData = DatabaseHandler.getSoundById(sound_id);
        if (soundData == null) {
            return null;
        }
        QueryResult tags = DatabaseHandler.getSoundTags(sound_id);
        List<Sound.Tag> tagList = new ArrayList<>();
        if (!tags.isEmpty()) {
            for (ResultRow tag : tags) {
                tagList.add(new Sound().new Tag(tag.getAsInt("id"), tag.get("name")));
            }
        }

        if (tagList.size() != 5) {
            for (int i = tagList.size(); i < 5; i++) {
                tagList.add(new Sound().new Tag(0, ""));
            }
        }

        return new Sound(soundData.get("id"), soundData.get("guild_id"), soundData.get("user_id"), soundData.get("name"), soundData.get("extension"), soundData.getAsBoolean("public"), soundData.getAsTimestamp("time"), tagList.toArray(new Sound.Tag[tagList.size()])); 
    }


    public static List<LayoutComponent> getSoundButton(String sound) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        ResultRow soundData = DatabaseHandler.getSoundById(sound);
        System.out.println(soundData.toString());
        Button id = Button.primary("sound-id", "ID: " + sound).asDisabled();
        Button name = Button.secondary("sound-name", soundData.get("name")).withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));
        Button isPrivate = Button.secondary("sound-private", " ").withEmoji(CustomEmojiHandler.getRichEmoji("lock"));

        if (soundData.getAsBoolean("public")) {
            isPrivate = Button.success("sound-private", " ").withEmoji(CustomEmojiHandler.getRichEmoji("lock"));
        }



        Button delete = Button.danger("sound-delete", " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));
        Button download = Button.secondary("sound-download", " ").withEmoji(CustomEmojiHandler.getRichEmoji("download"));

        buttonRows.add(ActionRow.of(
            id,
            name,
            isPrivate,
            download,
            delete
        ));

        QueryResult tags = DatabaseHandler.getSoundTags(sound);
        List<Button> tagButtons = new ArrayList<>();
        if (tags != null) {
            for (ResultRow tag : tags) {
                tagButtons.add(Button.primary("sound-tag-" + tag.get("id"), tag.get("name")).withEmoji(CustomEmojiHandler.getRichEmoji("tag")));
            }
            if (tagButtons.size() != 5) {
                for (int i = tagButtons.size(); i < 5; i++) {
                    tagButtons.add(Button.secondary("sound-tag-empty-" + i, " ").withEmoji(CustomEmojiHandler.getRichEmoji("blank")));
                }
            }
        }
        else {
            for (int i = 0; i < 5; i++) {
                tagButtons.add(Button.secondary("sound-tag-empty-" + i, " ").withEmoji(CustomEmojiHandler.getRichEmoji("blank")));
            }
        }

        buttonRows.add(ActionRow.of(tagButtons));
        

        return buttonRows;
    }

    public static List<LayoutComponent> getTagButton(String sound, String tag) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        ResultRow tagData = DatabaseHandler.getTag(tag);

        String name_tag = tagData.get("name") == null ? " " : tagData.get("name");
        
        Button back = Button.primary("tag-back-" + sound + "-" + tag, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button tagName = Button.success("tag-name-"+ sound + "-" + tag, name_tag).withEmoji(CustomEmojiHandler.getRichEmoji("tag"));
        Button delete = Button.danger("tag-delete-"+ sound + "-" + tag, " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(
            back,
            tagName,
            delete
        ));

        return buttonRows;
    }

    public static EmbedBuilder getSoundEmbed(Sound sound, User author) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(author.getName(), "https://github.com/SafJNest", author.getAvatarUrl());
        eb.setTitle("Playing now:");
        eb.setDescription("```" + sound.getName() + " (ID: " + sound.getId() + ") " + ((sound.isPublic()) ? ":public:" : ":private:") + "```");
        eb.setColor(Bot.getColor());
        eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.addField("Author", "```" 
            + Bot.getJDA().getUserById(sound.getUserId()).getName() 
        + "```", true);

        try {
            eb.addField("Lenght", "```"
                + (sound.isOpus()
                ? SafJNest.getFormattedDuration((Math.round(SoundBoard.getOpusDuration(sound.getPath())))*1000)
                : SafJNest.getFormattedDuration(sound.getAsTrack().getInfo().length))
            + "```", true);
        } catch (IOException e) {e.printStackTrace();}

        eb.addField("Format", "```" 
            + sound.getExtension().toUpperCase() 
        + "```", true);

        eb.addField("Guild", "```" 
            + Bot.getJDA().getGuildById(sound.getGuildId()).getName() 
        + "```", true);

        int[] plays = sound.getPlays(author.getId());
        eb.addField("Played", "```" 
            + plays[0]
            + (plays[0] == 1 ? " time" : " times") 
            + " (yours: "+ plays[1] + ")"
        + "```", true);

        Tag[] tags = sound.getTags();
        StringBuilder tagList = new StringBuilder();
        for(Tag tag : tags) {
            if (!tag.getName().isBlank()) tagList.append(tag.getName()).append(", ");
        }


        eb.addField("Tags", "```"
            + (!tagList.isEmpty() ? tagList.toString().substring(0, tagList.length() - 2) : " ")
            + "```", false);

        eb.addField("Creation time", 
            "<t:" + sound.getTimestampSecond() + ":f>"  + " | <t:" + sound.getTimestampSecond() + ":R>",
        false);

        return eb;
    }

}