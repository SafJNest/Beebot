package com.safjnest.lol.utils;

import com.safjnest.model.customemoji.CustomEmojiHandler;

import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class LaneTypeUtils {
  
  public static String getLaneTypeEmoji(LaneType type) {
    return switch (type) {
        case TOP -> CustomEmojiHandler.getFormattedEmoji("TopLane");
        case JUNGLE -> CustomEmojiHandler.getFormattedEmoji("Jungle");
        case MID -> CustomEmojiHandler.getFormattedEmoji("MidLane");
        case BOT -> CustomEmojiHandler.getFormattedEmoji("ADC");
        case UTILITY -> CustomEmojiHandler.getFormattedEmoji("Support");
        case NONE -> CustomEmojiHandler.getFormattedEmoji("autofill");
        default -> "";
    };
  }

  public static RichCustomEmoji getLaneTypeRichEmoji(LaneType type) {
      return switch (type) {
          case TOP -> CustomEmojiHandler.getRichEmoji("TopLane");
          case JUNGLE -> CustomEmojiHandler.getRichEmoji("Jungle");
          case MID -> CustomEmojiHandler.getRichEmoji("MidLane");
          case BOT -> CustomEmojiHandler.getRichEmoji("ADC");
          case UTILITY -> CustomEmojiHandler.getRichEmoji("Support");
          case NONE -> CustomEmojiHandler.getRichEmoji("autofill");
          default -> null;
      };
  }

  public static String getPrettyName(LaneType type) {
      return switch (type) {
          case TOP -> "Top Lane";
          case JUNGLE -> "Jungle";
          case MID -> "Mid Lane";
          case BOT -> "Bot Lane";
          case UTILITY -> "Support";
          case NONE -> "Remake Or NoLane";
          default -> type.name();
      };
  }
}
