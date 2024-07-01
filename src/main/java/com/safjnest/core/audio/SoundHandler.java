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

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.core.Bot;
import com.safjnest.core.CacheMap;
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
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Contains the methods to manage the soundboard.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.1
 */
public class SoundHandler {

    private static CacheMap<String, Sound> soundCache;

    public SoundHandler() {
        soundCache = new CacheMap<>(10L * 60 * 60 * 60, 10L * 60 * 60 * 60, 100);
    }   

    public static CacheMap<String, Sound> getSoundCache() {
        return soundCache;
    }

    /* -------------------------------------------------------------------------- */
    /*                              Sound Files                                   */
    /* -------------------------------------------------------------------------- */

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

    public static long getMP3Duration(String filePath) {
        try {
            Mp3File mp3file = new Mp3File(filePath);
            if (mp3file.hasId3v2Tag()) {
                // Duration in seconds
                return mp3file.getLengthInSeconds();
            } else {
                throw new Exception("MP3 file does not have an ID3v2 tag.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /* -------------------------------------------------------------------------- */
    /*                             Extract Sound                                  */
    /* -------------------------------------------------------------------------- */

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
        Sound sound = soundCache.get(sound_id);
        if (sound != null) return sound;
        
        ResultRow soundData = DatabaseHandler.getSoundById(sound_id);
        if (soundData == null) return null;
        
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

        sound = new Sound(soundData.get("id"), soundData.get("guild_id"), soundData.get("user_id"), soundData.get("name"), soundData.get("extension"), soundData.getAsBoolean("public"), soundData.getAsTimestamp("time"), tagList.toArray(new Sound.Tag[tagList.size()]));
        soundCache.put(sound_id, sound);
        return soundCache.get(sound_id); 
    }

    public static List<Sound> getSoundsByIds(String[] sound_ids) {
        List<Sound> sounds = new ArrayList<>();
        for (String sound_id : sound_ids) {
            Sound sound = soundCache.get(sound_id);
            if (sound != null) {
                sounds.add(sound);
            }
        }
        if (sounds.size() == sound_ids.length) return sounds;

        QueryResult soundsResult = DatabaseHandler.getSoundsById(sound_ids);
        QueryResult tags = DatabaseHandler.getSoundsTags(sound_ids);

        for (ResultRow soundData : soundsResult) {
            List<Sound.Tag> tagList = new ArrayList<>();
            for (ResultRow tag : tags) {
                if (tag.get("sound_id").equals(soundData.get("id"))) {
                    tagList.add(new Sound().new Tag(tag.getAsInt("id"), tag.get("name")));
                }
            }

            if (tagList.size() != 5) {
                for (int i = tagList.size(); i < 5; i++) {
                    tagList.add(new Sound().new Tag(0, ""));
                }
            }

            Sound sound = new Sound(soundData.get("id"), soundData.get("guild_id"), soundData.get("user_id"), soundData.get("name"), soundData.get("extension"), soundData.getAsBoolean("public"), soundData.getAsTimestamp("time"), tagList.toArray(new Sound.Tag[tagList.size()]));
            soundCache.put(sound.getId(), sound);
        }

        sounds = new ArrayList<>();
        for (String sound_id : sound_ids) {
            sounds.add(soundCache.get(sound_id));
        }
        return sounds;
    }

    /* -------------------------------------------------------------------------- */
    /*                             Extract Tag                                    */
    /* -------------------------------------------------------------------------- */

    public static Tag getTagById(String tag_id) {
        ResultRow tagData = DatabaseHandler.getTag(tag_id);
        if (tagData == null) return null;
        return new Sound().new Tag(tagData.getAsInt("id"), tagData.get("name"));
    }

    public static Tag getTagByName(String tag_name) {
        int tag_id  = DatabaseHandler.insertTag(tag_name.toLowerCase());
        return getTagById(String.valueOf(tag_id));
    }


    /* -------------------------------------------------------------------------- */
    /*                             Sound Embed                                    */
    /* -------------------------------------------------------------------------- */

    public static List<LayoutComponent> getSoundButton(String sound) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Sound soundData = SoundHandler.getSoundById(sound);

        Button id = Button.primary("sound-id", "ID: " + sound).asDisabled();
        Button name = Button.secondary("sound-name", soundData.getName()).withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));
        Button isPrivate = Button.secondary("sound-private", " ").withEmoji(CustomEmojiHandler.getRichEmoji("lock"));

        if (soundData.isPublic()) {
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

        Tag[] tags = soundData.getTags();
        List<Button> tagButtons = new ArrayList<>();
        int n = 0;
        for (Tag tag : tags) {
            if (tag.getId() == 0) tagButtons.add(Button.secondary("sound-tag-empty-" + n, " ").withEmoji(CustomEmojiHandler.getRichEmoji("blank")));
            else tagButtons.add(Button.primary("sound-tag-" + sound + "-" + tag.getId(), tag.getName()).withEmoji(CustomEmojiHandler.getRichEmoji("tag")));
            n++;
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


        String formattedDuration = "";
        try {
            if (sound.getAsTrack() != null) formattedDuration = SafJNest.getFormattedDuration(sound.getAsTrack().getDuration());
            else if (sound.getExtension().equals("opus")) formattedDuration = SafJNest.getFormattedDuration((Math.round(SoundHandler.getOpusDuration(sound.getPath())))*1000);
            else formattedDuration = SafJNest.getFormattedDuration((Math.round(SoundHandler.getMP3Duration(sound.getPath())))*1000);
        } catch (IOException e) {
            formattedDuration = "Error";
        }

        eb.addField("Lenght", "```"
        + formattedDuration
        + "```", true);



        eb.addField("Guild", "```" 
            + Bot.getJDA().getGuildById(sound.getGuildId()).getName() 
        + "```", true);


        Tag[] tags = sound.getTags();
        StringBuilder tagList = new StringBuilder();
        for(Tag tag : tags) {
            if (!tag.getName().isBlank()) tagList.append(tag.getName()).append(", ");
        }

        if (!tagList.isEmpty() && tagList.length() > 2) {
            eb.addField("Tags", "```"
                + tagList.toString().substring(0, tagList.length() - 2)
                + "```", false);
        }

        eb.addField("Creation time", 
            "<t:" + sound.getTimestampSecond() + ":f>"  + " | <t:" + sound.getTimestampSecond() + ":R>",
        false);

        int[] plays = sound.getPlays(author.getId());
        eb.setFooter("Listened: " + plays[1] + (plays[1] == 1 ? " time" : " times") + " (" + plays[0] + " total)");

        return eb;
    }

    public static List<LayoutComponent> getSoundEmbedButtons(Sound sound) {
        int[] likes = sound.getLikesDislikes();
        Button like = Button.primary("soundplay-like-" + sound.getId(), String.valueOf(likes[0])).withEmoji(CustomEmojiHandler.getRichEmoji("like"));
        Button dislike = Button.danger("soundplay-dislike-"+ sound.getId(), String.valueOf(likes[1])).withEmoji(CustomEmojiHandler.getRichEmoji("dislike"));
        Button replay = Button.success("soundplay-replay-"+ sound.getId(), " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        buttonRows.add(ActionRow.of(
            like,
            dislike,
            replay
        ));

        return buttonRows; 
    }

    /* -------------------------------------------------------------------------- */
    /*                             SoundBoard                                     */
    /* -------------------------------------------------------------------------- */


    public static List<Sound> getSoundboardSounds(String soundboardID) {
        QueryResult sounds = DatabaseHandler.getSoundsFromSoundBoard(soundboardID);
        return getSoundsByIds(sounds.arrayColumn("sound_id").toArray(new String[0]));
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String soundboardID) {
        String name = DatabaseHandler.getSoundboardByID(soundboardID).get("name");
        List<Sound> soundList = getSoundboardSounds(soundboardID);
        return composeSoundboard(event, name, soundList);
    }


    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String name, List<Sound> sounds) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName() + " requested:", "https://github.com/SafJNest", event.getUser().getAvatarUrl());
        eb.setTitle("**" + name + "**");
        eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());
        eb.setDescription("Press a button to play a sound");
        eb.setColor(Bot.getColor());
        eb.setFooter(sounds.size() + " sounds");
        
        List<LayoutComponent> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();
        for (int i = 0; i < sounds.size(); i++) {
            row.add(Button.primary("soundboard-" + sounds.get(i).getId() + "." + sounds.get(i).getExtension(), sounds.get(i).getName()));
            if (row.size() == 5 || i == sounds.size() - 1) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
        }

        return event.deferReply().addEmbeds(eb.build()).setComponents(rows);
    }

}