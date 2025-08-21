package com.safjnest.core.cache.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;//everybody was mocking you?
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusStatistics;

import com.mpatric.mp3agic.Mp3File;
import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.regex.Pattern;

/**
 * Contains the methods to manage the soundboard.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.1
 */
public class SoundCache extends CacheAdapter<String, Sound> {

    private static final SoundCache instance = new SoundCache();

    public SoundCache() {
        setExpireTime(2, TimeUnit.MINUTES);
        setTypeLimit(100);
    }   

    public Sound get(String key) {
        return super.get(key);
    }

    public List<Sound> get(String... keys) {
        return new ArrayList<>(super.get(List.of(keys)));
    }

    public static SoundCache getInstance() {
        return instance;
    }

    public static Sound getSoundByString(String regex, Guild guild, User user) {
        QueryCollection sounds = regex.matches("[0123456789]*") 
            ? BotDB.getSoundsById(regex, guild.getId(), user.getId()) 
            : BotDB.getSoundsByName(regex, guild.getId(), user.getId());
    
        if(sounds.isEmpty()) {
            return null;
        }
    
        QueryRecord toPlay = null;
        for(QueryRecord sound : sounds) {
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
        return getSoundsByIds(new String[] {sound_id}).get(0);
    }

    public static List<Sound> getSoundsByIds(String[] sound_ids) {
        if (instance.keySet().containsAll(List.of(sound_ids))) 
            return instance.get(sound_ids);

        List<String> notCached = List.of(sound_ids).stream().filter(id -> !instance.keySet().contains(id)).toList();

        QueryCollection soundsResult = BotDB.getSoundsById(notCached.toArray(new String[0]));
        QueryCollection tags = BotDB.getSoundsTags(notCached.toArray(new String[0]));

        for (QueryRecord soundData : soundsResult) {
            List<Tag> tagList = new ArrayList<>();
            for (QueryRecord tag : tags) {
                if (tag.get("sound_id").equals(soundData.get("id"))) {
                    tagList.add(new Tag(tag.getAsInt("tag_id"), tag.get("name")));
                }
            }

            if (tagList.size() != Tag.MAX_TAG_SOUND) {
                for (int i = tagList.size(); i < Tag.MAX_TAG_SOUND; i++) {
                    tagList.add(new Tag());
                }
            }

            Sound sound = new Sound(soundData, tagList);
            instance.put(sound.getId(), sound);
        }

        return instance.get(sound_ids);
    }

    public static boolean deleteSound(String id) {
        boolean result = BotDB.deleteSound(id);
        if (result) instance.remove(id);
        return result;
    }

    public static List<Sound> searchSound(String regex) {
        return searchSound(regex, null);
    } 

    
    public static List<Sound> searchSound(String regex, String author) {
        QueryCollection result = author == null ? BotDB.extremeSoundResearch(regex) : BotDB.extremeSoundResearch(regex, author);
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
        QueryRecord tagData = BotDB.getTag(tag_id);
        if (tagData == null) return null;
        return new Tag(tagData.getAsInt("id"), tagData.get("name"));
    }

    public static Tag getTagByName(String tag_name) {
        int tag_id  = BotDB.insertTag(tag_name.toLowerCase());
        return getTagById(String.valueOf(tag_id));
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

}