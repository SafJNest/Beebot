package com.safjnest.lol.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.model.customemoji.CustomEmojiHandler;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class ChampionUtils {

  private static Map<Integer, StaticChampion> champions = new HashMap<>();

  static {
    champions = LeagueHandler.getRiotApi().getDDragonAPI().getChampions()
        .entrySet()
        .stream()
        .filter(entry -> !entry.getValue().getKey().startsWith("Jade_"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static Map<Integer, StaticChampion> getChampions() {
    return champions;
  }

  public static StaticChampion getChampion(int id) {
    return champions.get(id);
  }

  public static StaticChampion getChampion(String name) {
    return findChampion(name);
  }

  public static StaticChampion findChampion(String name) {
    if (name == null || name.isBlank()) return null;

    String normalized = sanitizeChampionName(name).toLowerCase(Locale.ROOT);
    for (StaticChampion champion : champions.values()) {
      String championName = sanitizeChampionName(champion.getName()).toLowerCase(Locale.ROOT);
      if (championName.equals(normalized)) return champion;
    }
    return null;
  }

  public static List<String> getChampionsNames() {
    return champions.values().stream().map(champion -> champion.getName()).collect(Collectors.toList());
  }

  /**
   * Get the champion name that is more similar to the input such as "Kha'Zix" -> "Khazix"
   * @param champName
   * @return
   */
    public static String sanitizeChampionName(String champName) {
      champName = champName.replace(".", "");
      champName = champName.replace("i'S", "is");
      champName = champName.replace("a'Z", "az");
      champName = champName.replace("l'K", "lk");
      champName = champName.replace("o'G", "og");
      champName = champName.replace("g'M", "gm");
      champName = champName.replace("'", "");
      champName = champName.replace(" & Willump", "");
      champName = champName.replace(" ", "");
      return champName;
    }

  public static Emoji getEmojiByChampion(int championId) {
      StaticChampion champion = champions.get(championId);
      long emojiId = Long.parseLong(CustomEmojiHandler.getEmojiId(champion.getName()));
      return Emoji.fromCustom(champion.getName(), emojiId, false);
  }

  public static String getFormattedEmojiByChampion(int champion) {
      if (champion == -1) return CustomEmojiHandler.getFormattedEmoji("0");
      return CustomEmojiHandler.getFormattedEmoji(champions.get(champion).getName());
  }

  public static String getChampionProfilePic(int championId) {
    return getChampionProfilePic(String.valueOf(championId));
  }

  public static String getChampionProfilePic(String champ){
    return "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/"+ champ +".png";
  }

  public static String getChampionProfilePic(int champ, String skin){
      return "https://cdn.communitydragon.org/" + PatchUtils.getPatch() + "/champion/"+champ+"/tile/skin/" + skin;
  }
}
