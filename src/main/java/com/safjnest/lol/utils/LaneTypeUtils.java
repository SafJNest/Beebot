package com.safjnest.lol.utils;

import java.util.List;

import com.safjnest.model.customemoji.CustomEmojiHandler;

import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class LaneTypeUtils {

  private static final List<LaneType> PLAYABLES = List.of(
      LaneType.TOP, LaneType.JUNGLE, LaneType.MID, LaneType.BOT, LaneType.UTILITY
  );

  public static List<LaneType> playables() {
    return PLAYABLES;
  }

  /** Stable role name used by the public profile API. */
  public static String apiName(LaneType lane) {
    if (lane == null) return "AUTOFILL";
    return switch (lane) {
      case TOP -> "TOP";
      case JUNGLE -> "JUNGLE";
      case MID -> "MIDDLE";
      case BOT -> "BOTTOM";
      case UTILITY -> "SUPPORT";
      default -> "AUTOFILL";
    };
  }

  public static int playableOrder(LaneType lane) {
    return PLAYABLES.indexOf(lane) >= 0 ? PLAYABLES.indexOf(lane) : PLAYABLES.size();
  }
  
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

  public static LaneType oppositeLane(LaneType lane) {
    if (lane == null) return null;
    return switch (lane) {
        case UTILITY -> LaneType.BOT;
        case BOT     -> LaneType.UTILITY;
        default        -> lane;
    };
  }
}
