package com.safjnest.model.customemoji;

import java.util.HashMap;
import java.util.List;

import com.safjnest.core.Bot;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;

public class CustomEmojiHandler {
    
    private static HashMap<String, CustomEmoji> emoji;
    private static String[] ids = {"1106615853660766298", "1106615897952636930", "1106615926578761830", "1106615956685475991", "1106648612039041064", "1108673762708172811", "1117059269901164636", "1117060300592664677", "1117060763182452746", "1123678509693423738", "1131573980944416768", "1132405368119627869", "1132694780703416410", "1132694832305934439","1132636113568280636", "1132636703883014154", "1178343116504305776", "1188786505544630362", "1194737164202807379", "1239279325472100452", "1240322911101784066", "1240326461366337547"};

    public CustomEmojiHandler() {
        loadEmoji();
    }

    public static void loadEmoji() {
        JDA jda = Bot.getJDA();
        List<String> emojiServers = List.of(ids);

        emoji = new HashMap<>();
        for(Guild guild : jda.getGuilds()){
            if (emojiServers.contains(guild.getId())) {
                for(RichCustomEmoji em : guild.getEmojis()){
                    emoji.put(em.getName().toLowerCase(), new CustomEmoji(em.getId(), guild.getId(), em.getName(), em));
                }
            }
        }
    }

    public static String getFormattedEmoji(String name){
        if(name.equals("0") || name.equals("a0") || name.equals("2202_")) {
            return ":black_large_square:";
        }

        if(name.equals("2201_")) {
            name = "4_";
        }
        name = RiotHandler.transposeChampionNameForDataDragon(name);
        CustomEmoji em = emoji.get(name.toLowerCase());     
        return em != null ? emoji.get(name.toLowerCase()).toString() : String.valueOf(name);
    }

    public static RichCustomEmoji getRichEmoji(String name){
        if(name.equals("2201_")) {
            name = "4_";
        }

        name = RiotHandler.transposeChampionNameForDataDragon(name);
        return emoji.get(name.toLowerCase()).getObject();
       
    }

    public static String getFormattedEmoji(int name){
        if(name == 0) {
            return ":black_large_square:";
        }
        String ss = emoji.get(String.valueOf(name)).toString();            
        return ss != null ? ss : String.valueOf(name);
    }

    public static String getEmojiId(String name){
        name = RiotHandler.transposeChampionNameForDataDragon(name);
        CustomEmoji em = emoji.get(name.toLowerCase());
        return em != null ? em.getId() : name;
    }

    public static String[] getForbiddenServers() {
        return ids;
    }
}
