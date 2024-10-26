package com.safjnest.core.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;//everybody was mocking you?
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.utils.FileUpload;


import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusStatistics;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.core.Bot;
import com.safjnest.core.CacheMap;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TimeConstant;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import java.util.regex.Pattern;

/**
 * Contains the methods to manage the soundboard.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.1
 */
public class SoundHandler {

    private static CacheMap<String, Sound> soundCache;

    public SoundHandler() {
        soundCache = new CacheMap<>(TimeConstant.MINUTE, TimeConstant.MINUTE * 2, 100);
    }   

    public static CacheMap<String, Sound> getSoundCache() {
        return soundCache;
    }

//     ▄████████  ▄█   ▄█          ▄████████ 
//    ███    ███ ███  ███         ███    ███ 
//    ███    █▀  ███▌ ███         ███    █▀  
//   ▄███▄▄▄     ███▌ ███        ▄███▄▄▄     
//  ▀▀███▀▀▀     ███▌ ███       ▀▀███▀▀▀     
//    ███        ███  ███         ███    █▄  
//    ███        ███  ███▌    ▄   ███    ███ 
//    ███        █▀   █████▄▄██   ██████████ 
//                    ▀                      

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
//     ▄████████  ▄██████▄  ███    █▄  ███▄▄▄▄   ████████▄  
//    ███    ███ ███    ███ ███    ███ ███▀▀▀██▄ ███   ▀███ 
//    ███    █▀  ███    ███ ███    ███ ███   ███ ███    ███ 
//    ███        ███    ███ ███    ███ ███   ███ ███    ███ 
//  ▀███████████ ███    ███ ███    ███ ███   ███ ███    ███ 
//           ███ ███    ███ ███    ███ ███   ███ ███    ███ 
//     ▄█    ███ ███    ███ ███    ███ ███   ███ ███   ▄███ 
//   ▄████████▀   ▀██████▀  ████████▀   ▀█   █▀  ████████▀  
//                                                          

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
        if (soundCache.keySet().contains(sound_id)) return soundCache.get(sound_id);
        Sound sound = getSoundsByIds(new String[] {sound_id}).get(0);
        return sound;
    }

    public static List<Sound> getSoundsByIds(String[] sound_ids) {
        if (soundCache.keySet().containsAll(List.of(sound_ids))) 
            return soundCache.get(sound_ids);
        
        List<String> notCached = List.of(sound_ids).stream().filter(id -> !soundCache.keySet().contains(id)).toList();

        QueryResult soundsResult = DatabaseHandler.getSoundsById(notCached.toArray(new String[0]));
        QueryResult tags = DatabaseHandler.getSoundsTags(notCached.toArray(new String[0]));

        for (ResultRow soundData : soundsResult) {
            List<Tag> tagList = new ArrayList<>();
            for (ResultRow tag : tags) {
                if (tag.get("sound_id").equals(soundData.get("id"))) {
                    tagList.add(new Tag(tag.getAsInt("tag_id"), tag.get("name")));
                }
            }

            if (tagList.size() != Tag.MAX_TAG_SOUND) {
                for (int i = tagList.size(); i < Tag.MAX_TAG_SOUND; i++) {
                    tagList.add(new Tag());
                }
            }

            Sound sound = new Sound(soundData, tagList.toArray(new Tag[tagList.size()]));
            soundCache.put(sound.getId(), sound);
        }

        return soundCache.get(sound_ids);
    }

    public static boolean deleteSound(String id) {
        boolean result = DatabaseHandler.deleteSound(id);
        if (result) soundCache.remove(id);
        return result;
    }

    public static List<Sound> searchSound(String regex) {
        return searchSound(regex, null);
    } 

    
    public static List<Sound> searchSound(String regex, String author) {
        QueryResult result = author == null ? DatabaseHandler.extremeSoundResearch(regex) : DatabaseHandler.extremeSoundResearch(regex, author);
        List<Sound> sounds = getSoundsByIds(result.arrayColumn("id").toArray(new String[0]));
    
        final Pattern pattern = Pattern.compile(regex);
    
        Comparator<Sound> byRelevance = (Sound s1, Sound s2) -> {
            boolean s1Matches = pattern.matcher(s1.getName()).find();
            boolean s2Matches = pattern.matcher(s2.getName()).find();
    
            if (s1Matches && !s2Matches) {
                return -1;
            } else if (!s1Matches && s2Matches) {
                return 1;
            }

            int viewComparison = Integer.compare(s2.getGlobalPlays(), s1.getGlobalPlays());
            if (viewComparison != 0) {
                return viewComparison;
            }
            return Integer.compare(s2.getLikes(), s1.getLikes());
        };
        Collections.sort(sounds, byRelevance);
        int reduceTo = sounds.size() > 25 ? 25 : sounds.size();
        sounds = sounds.subList(0, reduceTo);
        return sounds;
    }

//      ███        ▄████████    ▄██████▄  
//  ▀█████████▄   ███    ███   ███    ███ 
//     ▀███▀▀██   ███    ███   ███    █▀  
//      ███   ▀   ███    ███  ▄███        
//      ███     ▀███████████ ▀▀███ ████▄  
//      ███       ███    ███   ███    ███ 
//      ███       ███    ███   ███    ███ 
//     ▄████▀     ███    █▀    ████████▀  
//                                        

    public static Tag getTagById(String tag_id) {
        ResultRow tagData = DatabaseHandler.getTag(tag_id);
        if (tagData == null) return null;
        return new Tag(tagData.getAsInt("id"), tagData.get("name"));
    }

    public static Tag getTagByName(String tag_name) {
        int tag_id  = DatabaseHandler.insertTag(tag_name.toLowerCase());
        return getTagById(String.valueOf(tag_id));
    }


//     ▄████████  ▄██████▄  ███    █▄  ███▄▄▄▄   ████████▄          ▄████████   ▄▄▄▄███▄▄▄▄   ▀█████████▄     ▄████████ ████████▄  
//    ███    ███ ███    ███ ███    ███ ███▀▀▀██▄ ███   ▀███        ███    ███ ▄██▀▀▀███▀▀▀██▄   ███    ███   ███    ███ ███   ▀███ 
//    ███    █▀  ███    ███ ███    ███ ███   ███ ███    ███        ███    █▀  ███   ███   ███   ███    ███   ███    █▀  ███    ███ 
//    ███        ███    ███ ███    ███ ███   ███ ███    ███       ▄███▄▄▄     ███   ███   ███  ▄███▄▄▄██▀   ▄███▄▄▄     ███    ███ 
//  ▀███████████ ███    ███ ███    ███ ███   ███ ███    ███      ▀▀███▀▀▀     ███   ███   ███ ▀▀███▀▀▀██▄  ▀▀███▀▀▀     ███    ███ 
//           ███ ███    ███ ███    ███ ███   ███ ███    ███        ███    █▄  ███   ███   ███   ███    ██▄   ███    █▄  ███    ███ 
//     ▄█    ███ ███    ███ ███    ███ ███   ███ ███   ▄███        ███    ███ ███   ███   ███   ███    ███   ███    ███ ███   ▄███ 
//   ▄████████▀   ▀██████▀  ████████▀   ▀█   █▀  ████████▀         ██████████  ▀█   ███   █▀  ▄█████████▀    ██████████ ████████▀  
//                                                                                                                                                                                                 

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
        int n = 1;
        for (Tag tag : tags) {
            Button tagButton = null;
            if (tag.isEmpty()) tagButton = Button.secondary("sound-tag-empty-" + n, " ").withEmoji(CustomEmojiHandler.getRichEmoji("tag"));
            else tagButton = Button.primary("sound-tag-" + sound + "-" + tag.getId(), tag.getName()).withEmoji(CustomEmojiHandler.getRichEmoji("tag"));

            tagButtons.add(tagButton);
            if (n % 5 == 0 || n == tags.length) {
                buttonRows.add(ActionRow.of(tagButtons));
                tagButtons = new ArrayList<>();
            }
            n++;
        }

        //buttonRows.add(ActionRow.of(tagButtons));
        

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
        eb.setFooter("Listened: " + plays[1] + (plays[1] == 1 ? " time" : " times") + " (" + sound.getGlobalPlays() + " total)");

        return eb;
    }

    public static List<LayoutComponent> getSoundEmbedButtons(Sound sound) {
        int[] likes = sound.getLikesDislikes(false);
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

//     ▄████████  ▄██████▄  ███    █▄  ███▄▄▄▄   ████████▄  ▀█████████▄   ▄██████▄     ▄████████    ▄████████ ████████▄  
//    ███    ███ ███    ███ ███    ███ ███▀▀▀██▄ ███   ▀███   ███    ███ ███    ███   ███    ███   ███    ███ ███   ▀███ 
//    ███    █▀  ███    ███ ███    ███ ███   ███ ███    ███   ███    ███ ███    ███   ███    ███   ███    ███ ███    ███ 
//    ███        ███    ███ ███    ███ ███   ███ ███    ███  ▄███▄▄▄██▀  ███    ███   ███    ███  ▄███▄▄▄▄██▀ ███    ███ 
//  ▀███████████ ███    ███ ███    ███ ███   ███ ███    ███ ▀▀███▀▀▀██▄  ███    ███ ▀███████████ ▀▀███▀▀▀▀▀   ███    ███ 
//           ███ ███    ███ ███    ███ ███   ███ ███    ███   ███    ██▄ ███    ███   ███    ███ ▀███████████ ███    ███ 
//     ▄█    ███ ███    ███ ███    ███ ███   ███ ███   ▄███   ███    ███ ███    ███   ███    ███   ███    ███ ███   ▄███ 
//   ▄████████▀   ▀██████▀  ████████▀   ▀█   █▀  ████████▀  ▄█████████▀   ▀██████▀    ███    █▀    ███    ███ ████████▀  
//                                                                                                 ███    ███            


    public static List<Sound> getSoundboardSounds(String soundboardID) {
        QueryResult sounds = DatabaseHandler.getSoundsFromSoundBoard(soundboardID);
        return getSoundsByIds(sounds.arrayColumn("sound_id").toArray(new String[0]));
    }

    public static boolean isValidThumbnail(Attachment thumbnail) {
        if (thumbnail == null) return false;
        if (thumbnail.getSize() > 1024 * 1024) return false;
        if (List.of("png", "jpg", "jpeg", "gif").stream().noneMatch(thumbnail.getFileExtension()::equals)) return false;
        return true;
    }

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String soundboardID) {
        ResultRow data = DatabaseHandler.getSoundboardByID(soundboardID);
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

    public static ReplyCallbackAction composeSoundboard(SlashCommandEvent event, String name, InputStream thumbnail, List<Sound> sounds) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName() + " requested:", "https://github.com/SafJNest", event.getUser().getAvatarUrl());
        eb.setTitle("**" + name + "**");
        
        if (thumbnail != null) eb.setThumbnail("attachment://thumbnail.png");
        else eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

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

        if (thumbnail != null) 
            return event.deferReply().addEmbeds(eb.build()).addFiles(FileUpload.fromData(thumbnail, "thumbnail.png")).setComponents(rows);
        return event.deferReply().addEmbeds(eb.build()).setComponents(rows);
    }





    public static EmbedBuilder getGreetViewEmbed(String userId, String guildId) {
        String globalGreetId = Bot.getUserData(userId).getGlobalGreet();
        String guildGreetId = Bot.getUserData(userId).getGuildGreet(guildId);

        Sound globalGreet = (globalGreetId != null && !globalGreetId.isBlank()) ? SoundHandler.getSoundById(globalGreetId) : null;
        Sound guildGreet = (guildGreetId != null && !guildGreetId.isBlank()) ? SoundHandler.getSoundById(guildGreetId) : null;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Your greet sounds");
        eb.setThumbnail(Bot.getJDA().getUserById(userId).getAvatarUrl());
        eb.setColor(Bot.getColor());
        eb.setDescription("A greet sound is a sound that will be played when you join a voice channel!.\n" +
            "You can set a global greet sound that will be played in every server, or a specific greet sound for each server.\n" +
            "Click on the buttons to set, change, listen or delete your greet");

        eb.addField("Global greet", "```" + (globalGreet != null ? globalGreet.getName() : "Not set") + "```", true);
        eb.addField("Guild greet", "```" + (guildGreet != null ? guildGreet.getName() : "Not set") + "```", true);

        return eb;
    }

    public static List<LayoutComponent> getGreetButton(String userId, String GuildId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        String globalGreetId = Bot.getUserData(userId).getGlobalGreet();
        String guildGreetId = Bot.getUserData(userId).getGuildGreet(GuildId);

        Sound globalGreet = (globalGreetId != null && !globalGreetId.isBlank()) ? SoundHandler.getSoundById(globalGreetId) : null;
        Sound guildGreet = (guildGreetId != null && !guildGreetId.isBlank()) ? SoundHandler.getSoundById(guildGreetId) : null;

        
        Button globalGreetButton, guildGreetButton, userButton = Button.primary("greet-user-" + userId, Bot.getUserData(userId).getName()).asDisabled().withEmoji(CustomEmojiHandler.getRichEmoji("user"));
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
            guildGreetButton
        ));

        return buttonRows;
    }

    public static List<LayoutComponent> getGreetSoundButton(String userId, String type, String soundId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Sound sound = SoundHandler.getSoundById(soundId);

        
        Button back = Button.primary("greet-back-" + userId, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button change = Button.success("greet-set-" + type, sound.getName()).withEmoji(CustomEmojiHandler.getRichEmoji("wavesound"));
        Button play = Button.primary("soundboard-" + sound.getId() + "." + sound.getExtension(), " ").withEmoji(CustomEmojiHandler.getRichEmoji("audio"));
        Button delete = Button.danger("greet-delete-" + type, " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(
            back,
            play,
            change,
            delete
        ));

        return buttonRows;
    }

}