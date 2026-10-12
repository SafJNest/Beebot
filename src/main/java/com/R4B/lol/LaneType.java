// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

import com.R4B.lol.*;

public enum LaneType{
    
    MID("MID", "MIDDLE", "MID_LANE"),

    TOP("TOP", "TOP_LANE"),

    JUNGLE("JUNGLE"),

    BOT("BOT", "BOTTOM", "BOT_LANE"),

    UTILITY("UTILITY"),

    AFK("AFK"),

    INVALID("Invalid"),

    NONE("NONE", "");
    
    private final String[] keys;
    
    LaneType(final String... keys)
    {
        this.keys = keys.clone();
    }

    public LaneType getFromCode(final String code)
    {
        return Stream.of(LaneType.values()).filter(t -> Stream.of(t.keys).anyMatch(s -> s.equalsIgnoreCase(code))).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case MID -> { return "Mid"; }
            case TOP -> { return "Top"; }
            case JUNGLE -> { return "Jungle"; }
            case BOT -> { return "Bot"; }
            case NONE -> { return "None"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }
    
    public String getValue()
    {
        return this.name();
    }

    public String[] getCodes()
    {
        return this.keys;
    }

    public static LaneType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

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

  public static LaneType oppositeLane(LaneType lane) {
    if (lane == null) return null;
    return switch (lane) {
        case UTILITY -> LaneType.BOT;
        case BOT     -> LaneType.UTILITY;
        default        -> lane;
    };
  }

  public static boolean isDuo(LaneType primary, LaneType ally) {
    return primary == LaneType.BOT && ally == LaneType.UTILITY
        || primary == LaneType.UTILITY && ally == LaneType.BOT;
  }

  public static boolean isDuoLane(LaneType lane) {
    return lane == LaneType.BOT || lane == LaneType.UTILITY;
  }}
