// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.Collectors;

import com.R4B.lol.*;
import com.R4B.lol.TierDivisionType.MmrRange;

import java.util.stream.*;

public enum TierType{
    CHALLENGER,
    GRANDMASTER,
    MASTER,
    DIAMOND,
    EMERALD,
    PLATINUM,
    GOLD,
    SILVER,
    BRONZE,
    IRON,
    UNRANKED;

    public TierType getFromCode(final String type)
    {
        return Stream.of(TierType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case CHALLENGER -> { return "Challenger"; }
            case GRANDMASTER -> { return "Grandmaster"; }
            case MASTER -> { return "Master"; }
            case DIAMOND -> { return "Diamond"; }
            case EMERALD -> { return "Emerald"; }
            case PLATINUM -> { return "Platinum"; }
            case GOLD -> { return "Gold"; }
            case SILVER -> { return "Silver"; }
            case BRONZE -> { return "Bronze"; }
            case IRON -> { return "Iron"; }
            case UNRANKED -> { return "Unranked"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getValue()
    {
        return this.name();
    }

    public static TierType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

  public static MmrRange getMmrRange(TierType tier) {
    if (tier == null) return new MmrRange(0, null);
    return switch (tier) {
      case IRON -> new MmrRange(0, 400);
      case BRONZE -> new MmrRange(400, 800);
      case SILVER -> new MmrRange(800, 1200);
      case GOLD -> new MmrRange(1200, 1600);
      case PLATINUM -> new MmrRange(1600, 2000);
      case EMERALD -> new MmrRange(2000, 2400);
      case DIAMOND -> new MmrRange(2400, 10000);
      case MASTER -> new MmrRange(10000, 20000);
      case GRANDMASTER -> new MmrRange(20000, 30000);
      case CHALLENGER -> new MmrRange(30000, null);
      case UNRANKED -> new MmrRange(-1, 0);
    };
  }

  public static TierType getTierFromMmr(long mmr) {
    for (TierType tier : TierType.values()) {
      if (tier == TierType.UNRANKED) continue;
      MmrRange range = getMmrRange(tier);
      if (mmr >= range.minimum() && (range.maximum() == null || mmr < range.maximum())) return tier;
    }
    return TierType.UNRANKED;
  }

  public static List<TierType> getHigherTiers(TierType tier) {
    return Arrays.asList(TierType.values()).stream()
      .filter(t -> t.ordinal() <= tier.ordinal())
      .collect(Collectors.toList());
  }}
